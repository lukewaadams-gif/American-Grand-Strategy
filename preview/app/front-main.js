// The site's front page: the game's title screen and setup (start.ts), on the engine of the game's own page (preview/),
// which then carries out the choice there. The page at the site's root loads this module from preview/app/.
import { engine, Refused, start } from './core/engine.js';
import { h } from './core/dom.js';
import { toast } from './core/dialog.js';
import { startScreen } from './start.js';
import { handOff } from './core/launch.js';
const root = document.getElementById('app');
/** The game's own page, next to this module's folder. */
const GAME = new URL('../index.html', import.meta.url).href;
function go(l) {
    if (!handOff(l, GAME))
        toast(l.kind === 'load' ? 'That file is too big to hand over here: open it from the game’s own page.' : 'Your browser kept nothing to hand over: try again.');
}
async function boot() {
    await start();
    document.body.className = 'presidency start-page';
    root.replaceChildren(startScreen({
        options: real => engine.options(real),
        newGame: game => go({ kind: 'new', game }),
        loadFile: text => go({ kind: 'load', text }),
        quick: (save, then) => go({ kind: 'quick', save, then }),
        islandChain: options => go({ kind: 'islandChain', options }),
        links: [{ label: 'The earlier playable slice', href: 'classic/' }, { label: 'The game’s own page', href: GAME }],
    }));
}
boot().catch(e => {
    root.replaceChildren(h('p', { class: 'boot-error', role: 'alert' }, 'The game could not start: ' + (e instanceof Refused ? e.message : String(e))
        + '. Reloading the page past the cache (Ctrl+Shift+R, or Cmd+Shift+R on a Mac) fetches the latest version.'));
    console.error(e);
});
