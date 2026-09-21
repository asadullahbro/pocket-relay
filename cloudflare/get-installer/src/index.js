// Serves the sms-forwarder install script at https://get.asdl.website
// (mirrors the get.docker.com pattern: `curl -sSL https://get.asdl.website | bash`).
//
// Always proxies GitHub's "latest release" asset, so this never needs to be
// redeployed when a new version of the installer ships -- only the release
// pipeline in the sms-forwarder repo needs to run.

const UPSTREAM =
  "https://github.com/asadullahbro/sms-forwarder/releases/latest/download/install.sh";

export default {
  async fetch(request) {
    const url = new URL(request.url);

    if (request.method !== "GET" && request.method !== "HEAD") {
      return new Response("Method not allowed", { status: 405 });
    }

    if (url.pathname === "/" || url.pathname === "/install.sh") {
      const upstreamResp = await fetch(UPSTREAM, {
        cf: { cacheTtl: 60, cacheEverything: true },
      });

      if (!upstreamResp.ok) {
        return new Response(
          "sms-forwarder: failed to fetch installer from GitHub releases\n",
          { status: 502 }
        );
      }

      return new Response(upstreamResp.body, {
        status: 200,
        headers: {
          "content-type": "text/x-shellscript; charset=utf-8",
          "cache-control": "public, max-age=60",
        },
      });
    }

    return new Response("Not found\n", { status: 404 });
  },
};
