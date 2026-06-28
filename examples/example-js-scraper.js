/**
 * Example JS plugin scraper for Wavestream.
 *
 * The runtime provides: fetch, URL, URLSearchParams, atob/btoa, console,
 * AbortController, TextEncoder/TextDecoder, Promise, require.
 *
 * Must export `getStreams(tmdbId, mediaType, season, episode)` returning
 * an array of stream objects:
 *   { url: "...", title: "...", quality: "1080p", headers: {...}, subtitles: [...] }
 *
 * For torrent streams, use infoHash + fileIdx + sources instead of url.
 */

module.exports.getStreams = async function(tmdbId, mediaType, season, episode) {
    // Example: fetch from a hypothetical API
    // var res = await fetch("https://api.example.com/streams?tmdbId=" + tmdbId);
    // var data = await res.json();
    // return data.streams;

    // For demo purposes, return hardcoded streams
    return [
        {
            url: "https://cdn.example.com/movie/" + tmdbId + "/1080p.mp4",
            title: "Direct 1080p",
            name: "ExampleJS 1080p",
            quality: "1080p",
            headers: { "Authorization": "Bearer demo-token" }
        },
        {
            url: "https://cdn.example.com/movie/" + tmdbId + "/720p.mp4",
            title: "Direct 720p",
            name: "ExampleJS 720p",
            quality: "720p"
        }
    ];
};

/**
 * Optional: define settings UI for the plugin.
 */
module.exports.onSettings = async function() {
    return [
        { id: "apiKey", label: "API Key", type: "text", placeholder: "Enter your API key" },
        { id: "defaultQuality", label: "Default Quality", type: "select", options: ["1080p", "720p", "480p"], default: "1080p" },
        { id: "enableSubtitles", label: "Enable Subtitles", type: "checkbox", default: true }
    ];
};
