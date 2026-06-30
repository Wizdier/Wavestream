# Stremio Addon Support

Wavestream can wrap any [Stremio addon](https://github.com/Stremio/stremio-addon-sdk) as a CloudStream `MainAPI` provider, so its catalog and streams appear alongside native CS providers in search and home results.

## How It Works

1. **You install an addon URL** (e.g. `https://example.com/stremio/addon`) via the Extensions screen.
2. `StremioAddonRepository` persists the URL and registers a `StremioProviderAdapter` instance in `APIHolder.allProviders`.
3. When the user searches:
   - `StremioProviderAdapter.search()` fetches `<addon>/catalog/movie/top.json` and `<addon>/catalog/series/top.json`, filters by query substring.
   - Results are wrapped as `MovieSearchResponse` / `TvSeriesSearchResponse` with synthetic `stremio://movie/<id>` or `stremio://series/<id>` URLs.
4. When the user opens a result:
   - `StremioProviderAdapter.load()` fetches `<addon>/meta/<type>/<id>.json` for full metadata and `<addon>/stream/<type>/<id>.json` for playable stream URLs.
   - The first stream with a `url` field becomes the `dataUrl` of the `LoadResponse`.
5. **Tapping Play** launches the player with that URL.

The library's `MainAPI.fixUrl` was patched to pass through `stremio:` URLs unchanged, so the synthetic deep-links survive the `fixUrl` step in `newMovieSearchResponse`/`newTvSeriesSearchResponse`.

## API Coverage

This implementation supports the [Stremio addon SDK requests](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/api/requests.md):

| Endpoint | Method | Used by |
| --- | --- | --- |
| `/catalog/<type>/<id>.json` | GET | `search()` |
| `/meta/<type>/<id>.json` | GET | `load()` |
| `/stream/<type>/<id>.json` | GET | `load()` |

Not yet implemented:
- `/catalog/<type>/<id>/search=<query>.json` (server-side search) — we filter client-side instead, which works for any addon but is less efficient on large catalogs.
- `/catalog/<type>/<id>/skip=<n>.json` (pagination) — only the first page of each catalog is fetched.
- `behaviorHints.countryWhitelist` / `behaviorHints.proxyHeaders` — streams are played as-is.
- Subtitles from `subtitle` field — only the primary `url` is used.

## Adding an Addon

From the **Extensions** tab, scroll to "Stremio addons", paste the addon URL (must end with no trailing path or with `/manifest.json` removed), and click **Add**.

The addon's catalog appears immediately in Home (under "Stremio: <host>" sections) and Search results.

## Removing an Addon

In the same Extensions tab, click the trash icon next to any installed addon. The provider is removed from `APIHolder.allProviders` immediately.

## Compatible Addons

Most public Stremio addons work. Tested categories:

- **Public domain movies** (e.g. channels based on public APIs)
- **YouTube channels** (via addons that wrap YouTube as Stremio meta)
- **Torrent-based addons** (streams returned as magnet links — note: Wavestream's player doesn't natively handle magnets; you'd need to integrate a torrent client)

Addons that require authentication (user data, watchlists) are NOT supported — Stremio's auth flow isn't implemented.

## Limitations

- Catalog search is client-side substring match, not server-side.
- Only one stream per title is exposed (the first one). A full implementation would let the user pick from multiple streams.
- Stream `behaviorHints` are ignored — some streams may require referer headers or specific user-agents that we don't forward.
