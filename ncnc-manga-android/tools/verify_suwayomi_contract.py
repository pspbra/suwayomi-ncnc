"""Read-only UI_LOGIN contract smoke test. Credentials come from environment or prompts.

SUWAYOMI_URL, SUWAYOMI_USERNAME, SUWAYOMI_PASSWORD may be provided by the caller.
No credentials, tokens, or manga titles are printed or saved.
"""

import getpass
import json
import os
import urllib.request


def main():
    url = os.environ.get("SUWAYOMI_URL") or input("Server URL: ").strip()
    username = os.environ.get("SUWAYOMI_USERNAME") or input("Username: ").strip()
    password = os.environ.get("SUWAYOMI_PASSWORD") or getpass.getpass("Password: ")

    def call(query, variables=None, token=None):
        headers = {"Content-Type": "application/json"}
        if token:
            headers["Authorization"] = "Bearer " + token
        request = urllib.request.Request(
            url.rstrip("/") + "/api/graphql",
            json.dumps({"query": query, "variables": variables or {}}).encode(),
            headers,
        )
        with urllib.request.urlopen(request, timeout=30) as response:
            result = json.load(response)
        assert not result.get("errors"), "GraphQL contract check failed: " + str(result.get("errors"))
        return result["data"]

    tokens = call(
        "mutation($u:String!,$p:String!){login(input:{username:$u,password:$p}){accessToken refreshToken}}",
        {"u": username, "p": password},
    )["login"]
    token = tokens["accessToken"]
    user = call("query { user { id username } }", token=token)["user"]
    assert user["username"].lower() == username.lower()
    print("PASS: UI_LOGIN and authenticated user identity")

    refreshed = call(
        "mutation($t:String!){refreshToken(input:{refreshToken:$t}){accessToken}}",
        {"t": tokens["refreshToken"]},
    )["refreshToken"]["accessToken"]
    assert call("query { user { id } }", token=refreshed)["user"]["id"] == user["id"]
    print("PASS: refresh without access token preserves user identity")

    history = call(
        'query { chapters(first:100,offset:0,filter:{lastReadAt:{greaterThan:"0"}},'
        'order:{by:LAST_READ_AT,byType:DESC}) { nodes { id mangaId isRead lastPageRead lastReadAt } } }',
        token=refreshed,
    )["chapters"]["nodes"]
    print("PASS: paginated per-user history and reading progress")

    shared = call(
        "query { serverLibrary(offset:0,limit:2,search:\"\") { totalCount hasNextPage "
        "items { manga { id inServerLibrary inLibrary } "
        "chapterCount downloadedChapterCount queuedChapterCount } } }",
        token=refreshed,
    )["serverLibrary"]
    assert shared["totalCount"] >= 0
    assert all(item["manga"]["inServerLibrary"] for item in shared["items"])
    print("PASS: shared server library and per-user favorite state")

    library = call(
        "query { mangas(condition:{inLibrary:true},first:1) { nodes { id thumbnailUrl } } sources(first:1) { nodes { id } } }",
        token=refreshed,
    )
    if history:
        record = history[0]
        chapter = call(
            "query($id:Int!){chapter(id:$id){id mangaId isRead lastPageRead lastReadAt}}",
            {"id": record["id"]}, refreshed,
        )["chapter"]
        assert chapter["lastPageRead"] == record["lastPageRead"]
        call(
            "query($id:Int!){chapters(condition:{mangaId:$id},order:{by:SOURCE_ORDER,byType:DESC})"
            "{nodes{id mangaId sourceOrder name chapterNumber uploadDate scanlator isRead lastPageRead lastReadAt}}}",
            {"id": record["mangaId"]}, refreshed,
        )
        print("PASS: single chapter and chapter-list progress fields")

    # GraphQL validates all fields/arguments, but @skip prevents the resolver and any write.
    call(
        "mutation { updateChapter(input:{id:0,patch:{lastPageRead:0,isRead:false}}) @skip(if:true)"
        "{chapter{id mangaId isRead lastPageRead lastReadAt}} }",
        token=refreshed,
    )
    print("PASS: progress mutation schema (resolver skipped; no data changed)")
    if library["mangas"]["nodes"]:
        path = library["mangas"]["nodes"][0].get("thumbnailUrl")
        if path:
            from urllib.parse import urljoin, urlsplit
            image_url = urljoin(url.rstrip("/") + "/", path)
            if urlsplit(image_url).netloc == urlsplit(url).netloc:
                request = urllib.request.Request(image_url, headers={"Authorization": "Bearer " + refreshed})
                with urllib.request.urlopen(request, timeout=30) as response:
                    assert response.headers.get("Content-Type", "").startswith("image/")
                    response.read(64)
                print("PASS: authenticated cover image")
    print("Read-only contract checks complete; Android build/device tests are separate.")


if __name__ == "__main__":
    main()
