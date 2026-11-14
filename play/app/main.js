// The preview of the campaign: the war room, live over the real engine. It opens a real mid-campaign save (week 9 of
// 16) and plays from there. The whole game, from a new campaign to the verdict on a presidency, is index.html.
import { buildFile, engine, start } from './core/engine.js';
import { h } from './core/dom.js';
import { usePalette } from './campaign/palette.js';
import { mountCampaign } from './campaign/app.js';
const root = document.getElementById('app');
let saveText = '';
function restart() { engine.load(saveText); mountCampaign({ restart }); }
async function boot() {
    const params = new URLSearchParams(location.search);
    usePalette(params.get('palette') ?? 'standard');
    if (params.get('motion') === 'reduce')
        document.documentElement.dataset['motion'] = 'reduce';
    if (params.get('size'))
        document.documentElement.style.setProperty('--s', params.get('size'));
    await start();
    saveText = await buildFile(params.get('save') ?? 'saves/campaign.json');
    engine.load(saveText);
    mountCampaign({ restart });
}
boot().catch(e => {
    root.replaceChildren(h('p', { class: 'boot-error', role: 'alert' }, 'The game could not start: ' + String(e)
        + '. Reloading the page past the cache (Ctrl+Shift+R, or Cmd+Shift+R on a Mac) fetches the latest version.'));
    console.error(e);
});
