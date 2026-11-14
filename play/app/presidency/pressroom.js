// The Press Briefing Room (Resolute Desk's PressRoom; docs/PRESIDENCY_PORT.md, milestone 6): the outlets and how they
// cover the president, press conferences and interviews, addresses to the nation and the State of the Union, posts,
// polls, and the party's wings and the opposition. A press conference or a speech is a run of questions on the desk.
import { h } from '../core/dom.js';
import { day } from '../core/format.js';
const TABS = ['The press', 'Speeches', 'Social media', 'Your party', 'Your term'];
function meter(v, label) {
    const tone = v >= 60 ? 'good' : v >= 40 ? 'mid' : 'bad';
    return h('span', { class: 'hill-meter ' + tone, role: 'meter', 'aria-valuemin': '0', 'aria-valuemax': '100', 'aria-valuenow': String(Math.round(v)), 'aria-label': label }, h('span', { style: `width:${Math.max(2, Math.min(100, v))}%` }), h('span', { class: 'hill-meter-value' }, String(Math.round(v))));
}
export function pressRoom(act, first = 'The press') {
    let tab = first;
    let outlet = -1, pollIssue = '', addressIssue = '', postIssue = '', tone = 1;
    const root = h('div', { class: 'room hill press' });
    const order = (o, thenDecide = false) => { if (act.order(o)) {
        draw();
        if (thenDecide)
            act.decide();
    } };
    function issueSelect(label, value, set, issues) {
        if (!value)
            set(issues[0].id);
        return h('select', { 'aria-label': label, onchange: (e) => set(e.target.value) }, ...issues.map(i => h('option', { value: i.id, selected: i.id === value }, i.label)));
    }
    function draw() {
        const v = act.view();
        const top = h('div', { class: 'room-top' }, h('div', null, h('div', { class: 'eyebrow' }, 'Down the hall'), h('h2', { class: 'dialog-title' }, 'The Press Briefing Room'), h('p', { class: 'muted' }, 'Week of ' + day(v.date, true))), h('button', { class: 'btn primary', type: 'button', onclick: act.back }, 'Back to the Oval Office'));
        if (!v.outlets) {
            root.replaceChildren(top, h('p', null, v.reason ?? ''));
            return;
        }
        const nav = h('nav', { class: 'cab-tabs', 'aria-label': 'The Press Briefing Room' }, ...TABS.map(t => h('button', { class: 'btn small toggle' + (t === tab ? ' on' : ''), type: 'button', 'aria-pressed': String(t === tab), onclick: () => { tab = t; draw(); } }, t)));
        const body = tab === 'The press' ? pressTab(v) : tab === 'Speeches' ? speechTab(v) : tab === 'Social media' ? socialTab(v)
            : tab === 'Your party' ? partyTab(v) : termTab();
        root.replaceChildren(top, nav, ...(v.busy ? [h('p', { class: 'hill-closed', role: 'note' }, 'A press conference or a speech is under way: its questions are on your desk.', h('button', { class: 'btn small', type: 'button', onclick: act.decide }, 'Continue'))] : []), body);
    }
    function pressTab(v) {
        const sitDowns = v.outlets.filter(o => o.interviews);
        if (outlet < 0 || !sitDowns.some(o => o.index === outlet))
            outlet = sitDowns[0]?.index ?? -1;
        return h('div', { class: 'hill-body' }, h('div', { class: 'room-grid two' }, h('section', { class: 'room-card', 'aria-labelledby': 'pr-face' }, h('h3', { class: 'room-card-title', id: 'pr-face' }, 'Face the press'), h('p', { class: 'small' }, `Press relations ${v.relations} of 100. A press conference is three questions from the briefing-room seats; an interview, three from one host, its weight set by the audience.`), h('div', { class: 'cab-buttons' }, h('button', { class: 'btn small primary', type: 'button', disabled: !v.open || v.busy || v.presserIn > 0, onclick: () => order({ type: 'PressConference' }, true) }, v.presserIn > 0 ? `A press conference: next week` : 'Hold a press conference')), h('label', { class: 'hill-field' }, h('span', null, 'Sit down with'), h('select', { 'aria-label': 'The outlet for an interview', onchange: (e) => { outlet = Number(e.target.value); } }, ...sitDowns.map(o => h('option', { value: String(o.index), selected: o.index === outlet }, `${o.name} (${o.audience}M)`)))), h('button', { class: 'btn small', type: 'button', disabled: !v.open || v.busy || v.interviewIn > 0, onclick: () => order({ type: 'Interview', outlet }, true) }, v.interviewIn > 0 ? 'An interview: next week' : 'Give the interview'), h('p', { class: 'muted small' }, `${v.pressers} press conferences and ${v.interviews} interviews so far.`)), h('section', { class: 'room-card', 'aria-labelledby': 'pr-poll' }, h('h3', { class: 'room-card-title', id: 'pr-poll' }, 'Commission a poll'), h('label', { class: 'hill-field' }, h('span', null, 'On'), issueSelect('The poll’s issue', pollIssue, x => { pollIssue = x; }, v.issues)), h('button', { class: 'btn small', type: 'button', disabled: !v.open, onclick: () => order({ type: 'Poll', issue: pollIssue }) }, 'Run it ($0.5 million of party money)'), v.lastPoll ? h('p', { class: 'small' }, v.lastPoll) : h('p', { class: 'muted small' }, 'No poll yet.'))), h('section', { class: 'room-card', 'aria-labelledby': 'pr-out' }, h('h3', { class: 'room-card-title', id: 'pr-out' }, 'How the outlets cover you'), h('table', { class: 'hill-members' }, h('thead', null, h('tr', null, h('th', { scope: 'col' }, 'Outlet'), h('th', { scope: 'col' }, 'Audience'), h('th', { scope: 'col' }, 'Tone'))), h('tbody', null, ...v.outlets.map(o => h('tr', null, h('th', { scope: 'row' }, o.name), h('td', null, `${o.audience}M`), h('td', null, meter(o.tone, `${o.name}: tone`), h('span', { class: 'muted small' }, ' ' + o.label))))))));
    }
    function speechTab(v) {
        return h('div', { class: 'hill-body' }, h('div', { class: 'room-grid two' }, h('section', { class: 'room-card', 'aria-labelledby': 'pr-addr' }, h('h3', { class: 'room-card-title', id: 'pr-addr' }, 'An address from the Oval Office'), h('p', { class: 'small' }, 'Prime time on every network: an opening, three passages and a close, each your choice. The networks give you airtime every eight weeks.'), h('label', { class: 'hill-field' }, h('span', null, 'About'), issueSelect('The address’s subject', addressIssue, x => { addressIssue = x; }, v.issues)), h('button', { class: 'btn small primary', type: 'button', disabled: !v.open || v.busy || v.addressIn > 0, onclick: () => order({ type: 'Address', issue: addressIssue }, true) }, v.addressIn > 0 ? `Airtime in ${v.addressIn} weeks` : 'Request airtime for tonight')), h('section', { class: 'room-card', 'aria-labelledby': 'pr-sotu' }, h('h3', { class: 'room-card-title', id: 'pr-sotu' }, 'The State of the Union'), h('p', null, `The next is in the week of ${day(v.sotu, true)}. It is given before a joint session of Congress, and counts for more than an address.`), h('p', { class: 'muted small' }, `${v.speeches} speeches so far.`))));
    }
    function socialTab(v) {
        return h('div', { class: 'hill-body' }, h('section', { class: 'room-card', 'aria-labelledby': 'pr-post' }, h('h3', { class: 'room-card-title', id: 'pr-post' }, 'Post'), h('label', { class: 'hill-field' }, h('span', null, 'About'), issueSelect('The post’s issue', postIssue, x => { postIssue = x; }, v.issues)), h('div', { class: 'cab-buttons', role: 'group', 'aria-label': 'The tone' }, ...v.tones.map((t, i) => h('button', { class: 'btn small toggle' + (i === tone ? ' on' : ''), type: 'button',
            'aria-pressed': String(i === tone), onclick: () => { tone = i; draw(); } }, t))), h('p', { class: 'muted small' }, 'Attacks, jokes and personal posts reach further and backfire more often.'), h('button', { class: 'btn small primary', type: 'button', disabled: !v.open || v.postsLeft <= 0, onclick: () => order({ type: 'Post', issue: postIssue, tone }) }, v.postsLeft > 0 ? `Post it (${v.postsLeft} left this week)` : 'No more posts this week'), h('p', { class: 'small' }, `${v.followers} million followers.`)));
    }
    function partyTab(v) {
        return h('div', { class: 'hill-body' }, h('div', { class: 'room-grid two' }, h('section', { class: 'room-card', 'aria-labelledby': 'pr-wings' }, h('h3', { class: 'room-card-title', id: 'pr-wings' }, 'Your party'), h('dl', { class: 'ww-stats' }, ...v.wings.flatMap(w => [h('dt', null, w.name), h('dd', null, meter(w.mood, `${w.name}: satisfaction`))])), h('p', null, v.record + '.'), h('p', { class: 'muted small' }, 'Each wing judges how close your laws, orders and policy moves sit to it, and how you are doing. A content wing keeps its members in Congress with you.')), h('section', { class: 'room-card', 'aria-labelledby': 'pr-opp' }, h('h3', { class: 'room-card-title', id: 'pr-opp' }, 'The opposition'), h('p', null, 'Their strategy: ' + v.opposition.toLowerCase() + '.'), (v.oppLeaders ?? []).length ? h('ul', { class: 'hill-list' }, ...(v.oppLeaders ?? []).map(l => h('li', null, l))) : null, h('p', { class: 'muted small' }, 'They choose every quarter by your approval, and harden as an election nears.'))));
    }
    function termTab() {
        const t = act.terms?.();
        if (!t || t.term === undefined)
            return h('div', { class: 'hill-body' }, h('p', { class: 'muted' }, 'Your term begins at the inauguration.'));
        const r = t.race;
        const ordinal = t.term === 1 ? 'first' : 'second';
        const status = h('section', { class: 'room-card', 'aria-labelledby': 'pr-term' }, h('h3', { class: 'room-card-title', id: 'pr-term' }, `Your ${ordinal} term`), h('p', null, `Week ${t.termWeek} of the ${ordinal} term. ${t.running}.`), t.leaving ? h('p', { class: 'hill-closed', role: 'note' }, `${t.successor} takes the oath on ${day(t.handoff, true)}. Until then you are still the president: pardons, last orders and rules, and the lame-duck session of Congress.`) : null, h('dl', { class: 'ww-stats' }, h('dt', null, 'Lame duck'), h('dd', null, meter(t.lameDuck ?? 0, 'How far members and nominees look past you'))), h('p', { class: 'muted small' }, 'As the end nears, members of Congress look past you and your pull on their votes fades; faster after a second term\'s midterms, entirely once a successor is chosen.'), t.challenger ? h('p', { class: 'small' }, `${t.challenger} is challenging you in the primaries, with ${t.challengerShare}% of the vote so far.`) : null);
        const goals = h('section', { class: 'room-card', 'aria-labelledby': 'pr-goals' }, h('h3', { class: 'room-card-title', id: 'pr-goals' }, t.theme ? 'The second term: ' + t.theme.toLowerCase() : 'Legacy goals'), (t.goals ?? []).length
            ? h('ul', { class: 'hill-list' }, ...(t.goals ?? []).map(g => h('li', null, h('span', { class: 'ww-goal ' + (g.met ? 'good' : 'mid') }, g.met ? 'On track' : 'Not yet'), ' ' + g.label)))
            : h('p', { class: 'muted small' }, 'A re-elected president chooses what the second term is about; the theme sets three goals the verdict on your presidency counts.'));
        const cards = [h('div', { class: 'room-grid two' }, status, goals)];
        if (r) {
            const rows = r.rivals.map(c => h('tr', null, h('th', { scope: 'row' }, c.name, c.nominee ? h('span', { class: 'muted small' }, ' (nominee)') : null, c.endorsed ? h('span', { class: 'muted small' }, ' (endorsed)') : null), h('td', null, c.role || c.home), h('td', null, r.contests ? `${c.share}%` : '—'), h('td', null, String(c.delegates)), h('td', null, r.canEndorse ? h('button', { class: 'btn small', type: 'button', onclick: () => { if (act.termsOrder?.({ type: 'Endorse', rival: c.index }))
                    draw(); } }, 'Endorse') : null)));
            cards.push(h('section', { class: 'room-card', 'aria-labelledby': 'pr-race' }, h('h3', { class: 'room-card-title', id: 'pr-race' }, `The race to succeed you, ${r.year}`), h('table', { class: 'hill-members' }, h('thead', null, h('tr', null, h('th', { scope: 'col' }, 'Candidate'), h('th', { scope: 'col' }, 'Now'), h('th', { scope: 'col' }, 'Primary vote'), h('th', { scope: 'col' }, 'Delegates'), h('th', { scope: 'col', 'aria-label': 'Endorse' }))), h('tbody', null, ...rows)), h('p', { class: 'small' }, r.split > 40 ? `The primaries have split the party (bitterness ${r.split} of 100).` : r.contests ? 'The party is holding together.' : 'The primaries begin in January.'), r.primaries.length ? h('ul', { class: 'hill-list small' }, ...r.primaries.map(p => h('li', null, p.replace(/^\d{4}-\d{2}-\d{2}: /, '')))) : null, r.nominee && r.opponent ? h('p', null, r.done
                ? `${r.won ? r.nominee.name : r.opponent.name} won, ${Math.max(...r.ev)} electoral votes to ${Math.min(...r.ev)}.`
                : `${r.nominee.name} against ${r.opponent.name}: ${(r.national ?? 0) >= 0 ? 'ahead' : 'behind'} by ${Math.abs(r.national ?? 0).toFixed(1)} points nationally; the polls point to ${r.forecast} electoral votes.`) : null, h('p', { class: 'muted small' }, r.season ? 'Campaign season: your campaign stops on the calendar count for the nominee, and the party\'s money buys the ads.'
                : 'Your approval rubs off on the nominee. From a hundred days out, campaign stops on your calendar count, and so does the party\'s war chest.')));
        }
        return h('div', { class: 'hill-body' }, ...cards);
    }
    draw();
    return root;
}
