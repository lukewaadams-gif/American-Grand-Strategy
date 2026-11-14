// The preview of the presidency: the Oval Office over a save in the first hundred days (or ?save=, for example
// saves/governing.json). The whole game, from a new campaign to the verdict on a presidency, is index.html.
import { buildFile, engine, start } from './core/engine.js';
import { h } from './core/dom.js';
import { usePalette } from './campaign/palette.js';
import { mountPresidency } from './presidency/app.js';
const root = document.getElementById('app');
const params = new URLSearchParams(location.search);
let saveText = '';
function restart() { engine.load(saveText); mountPresidency({ restart }); }
async function boot() {
    usePalette(params.get('palette') ?? 'standard');
    if (params.get('palette'))
        document.documentElement.dataset['palette'] = params.get('palette');
    if (params.get('motion') === 'reduce')
        document.documentElement.dataset['motion'] = 'reduce';
    if (params.get('size'))
        document.documentElement.style.setProperty('--s', params.get('size'));
    await start();
    saveText = await buildFile(params.get('save') ?? 'saves/presidency.json');
    engine.load(saveText);
    mountPresidency({ restart });
}
boot().catch(e => {
    root.replaceChildren(h('p', { class: 'boot-error', role: 'alert' }, 'The game could not start: ' + String(e)
        + '. Reloading the page past the cache (Ctrl+Shift+R, or Cmd+Shift+R on a Mac) fetches the latest version.'));
    console.error(e);
});
