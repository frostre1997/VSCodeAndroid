(function () {
    'use strict';

    // Polyfills for older Android System WebView
    if (typeof Promise !== 'undefined' && !Promise.prototype.finally) {
        Promise.prototype.finally = function (cb) {
            var p = this.constructor;
            return this.then(
                function (v) { return p.resolve(cb()).then(function () { return v; }); },
                function (e) { return p.resolve(cb()).then(function () { throw e; }); }
            );
        };
    }
    if (typeof Array.prototype.find !== 'function') {
        Array.prototype.find = function (pred) {
            for (var i = 0; i < this.length; i++) {
                if (pred(this[i], i, this)) return this[i];
            }
            return undefined;
        };
    }

    function qsa(selector, fn) {
        Array.prototype.forEach.call(document.querySelectorAll(selector), fn);
    }

    // ------------------------------------------------------------------
    // Native bridge
    // ------------------------------------------------------------------

    window._medianGitEmit = window._medianGitEmit || function (name, data) {
        document.dispatchEvent(new CustomEvent('median-git:' + name, { detail: data }));
    };

    function call(command, params) {
        var cb = '_gitcb_' + Math.random().toString(36).slice(2);
        return new Promise(function (resolve, reject) {
            window[cb] = function (r) {
                delete window[cb];
                if (r && r.success) resolve(r.data);
                else reject(new Error(r && r.error ? r.error : 'Git error'));
            };
            GitBridge.invoke(command, JSON.stringify(params || {}), cb);
        });
    }

    // ------------------------------------------------------------------
    // Icons (Material Design paths, 24x24)
    // ------------------------------------------------------------------

    var ICONS = {
        sourceControl: 'M7,18c-1.66,0 -3,-1.34 -3,-3s1.34,-3 3,-3s3,1.34 3,3s-1.34,3 -3,3zM7,8C5.34,8 4,6.66 4,5s1.34,-3 3,-3s3,1.34 3,3s-1.34,3 -3,3zM7,10c-2.76,0 -5,2.24 -5,5s2.24,5 5,5s5,-2.24 5,-5s-2.24,-5 -5,-5zM18,8c-1.66,0 -3,-1.34 -3,-3s1.34,-3 3,-3s3,1.34 3,3s-1.34,3 -3,3zM18,10c-2.76,0 -5,2.24 -5,5s2.24,5 5,5s5,-2.24 5,-5s-2.24,-5 -5,-5z',
        sync: 'M12,4V1L8,5l4,4V6c3.31,0 6,2.69 6,6c0,1.01 -0.25,1.97 -0.7,2.8l1.46,1.46C19.54,15.03 20,13.57 20,12C20,7.58 16.42,4 12,4zM12,18c-3.31,0 -6,-2.69 -6,-6c0,-1.01 0.25,-1.97 0.7,-2.8L5.24,7.74C4.46,8.97 4,10.43 4,12c0,4.42 3.58,8 8,8v3l4,-4l-4,-4V18z',
        branch: 'M7,18c-1.66,0 -3,-1.34 -3,-3s1.34,-3 3,-3s3,1.34 3,3s-1.34,3 -3,3zM7,8C5.34,8 4,6.66 4,5s1.34,-3 3,-3s3,1.34 3,3s-1.34,3 -3,3zM7,10c-2.76,0 -5,2.24 -5,5s2.24,5 5,5s5,-2.24 5,-5s-2.24,-5 -5,-5zM18,8c-1.66,0 -3,-1.34 -3,-3s1.34,-3 3,-3s3,1.34 3,3s-1.34,3 -3,3zM18,10c-2.76,0 -5,2.24 -5,5s2.24,5 5,5s5,-2.24 5,-5s-2.24,-5 -5,-5z',
        history: 'M11.99,2C6.47,2 2,6.48 2,12s4.47,10 9.99,10C17.52,22 22,17.52 22,12S17.52,2 11.99,2zM12,20c-4.42,0 -8,-3.58 -8,-8s3.58,-8 8,-8s8,3.58 8,8S16.42,20 12,20zM12.5,7H11v6l5.25,3.15l0.75,-1.23l-4.5,-2.67V7z',
        folder: 'M20,6h-8l-2,-2H4C2.9,4 2.01,4.9 2.01,6L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V8C22,6.9 21.1,6 20,6z',
        gear: 'M19.14,12.94c0.04,-0.3 0.06,-0.61 0.06,-0.94c0,-0.32 -0.02,-0.64 -0.07,-0.94l2.03,-1.58c0.18,-0.14 0.23,-0.41 0.12,-0.61l-1.92,-3.32c-0.12,-0.22 -0.37,-0.29 -0.59,-0.22l-2.39,0.96c-0.5,-0.38 -1.03,-0.7 -1.62,-0.94L14.4,2.81c-0.04,-0.24 -0.24,-0.41 -0.48,-0.41h-3.84c-0.24,0 -0.43,0.17 -0.47,0.41L9.25,5.35C8.66,5.59 8.12,5.92 7.63,6.29L5.24,5.33c-0.22,-0.08 -0.47,0 -0.59,0.22L2.74,8.87C2.62,9.08 2.66,9.34 2.86,9.48l2.03,1.58C4.84,11.36 4.8,11.69 4.8,12s0.02,0.64 0.07,0.94l-2.03,1.58c-0.18,0.14 -0.23,0.41 -0.12,0.61l1.92,3.32c0.12,0.22 0.37,0.29 0.59,0.22l2.39,-0.96c0.5,0.38 1.03,0.7 1.62,0.94l0.36,2.54c0.05,0.24 0.24,0.41 0.48,0.41h3.84c0.24,0 0.44,-0.17 0.47,-0.41l0.36,-2.54c0.59,-0.24 1.13,-0.56 1.62,-0.94l2.39,0.96c0.22,0.08 0.47,0 0.59,-0.22l1.92,-3.32c0.12,-0.22 0.07,-0.47 -0.12,-0.61L19.14,12.94zM12,15.6c-1.98,0 -3.6,-1.62 -3.6,-3.6s1.62,-3.6 3.6,-3.6s3.6,1.62 3.6,3.6S13.98,15.6 12,15.6z',
        code: 'M9.4,16.6L4.8,12l4.6,-4.6L8,6l-6,6l6,6l1.4,-1.4zM14.6,16.6l4.6,-4.6l-4.6,-4.6L16,6l6,6l-6,6L14.6,16.6z',
        plus: 'M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6V13z',
        trash: 'M6,19c0,1.1 0.9,2 2,2h8c1.1,0 2,-0.9 2,-2V7H6V19zM19,4h-3.5l-1,-1h-5l-1,1H5v2h14V4z',
        chevronRight: 'M10,6L8.59,7.41 13.17,12l-4.58,4.59L10,18l6,-6z',
        copy: 'M16,1H4C2.9,1 2,1.9 2,3v14h2V3h12V1zM19,5H8C6.9,5 6,5.9 6,7v14c0,1.1 0.9,2 2,2h11c1.1,0 2,-0.9 2,-2V7C21,5.9 20.1,5 19,5zM19,21H8V7h11V21z',
        refresh: 'M17.65,6.35C16.2,4.9 14.21,4 12,4c-4.42,0 -7.99,3.58 -7.99,8s3.57,8 7.99,8c3.73,0 6.84,-2.55 7.73,-6h-2.08c-0.82,2.33 -3.04,4 -5.65,4c-3.31,0 -6,-2.69 -6,-6s2.69,-6 6,-6c1.66,0 3.14,0.69 4.22,1.78L13,11h7V4L17.65,6.35z',
        key: 'M12.65,10C11.83,7.67 9.61,6 7,6c-3.31,0 -6,2.69 -6,6s2.69,6 6,6c2.61,0 4.83,-1.67 5.65,-4H17v4h4v-4h2v-4H12.65zM7,14c-1.1,0 -2,-0.9 -2,-2s0.9,-2 2,-2s2,0.9 2,2S8.1,14 7,14z',
        compare: 'M10,3L5,3c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h5v2h2V1h-2V3zM10,19H5V5h5V19zM14,5v2h5v12h-5v-2h-2V5H14zM14,21v-2h5v2H14z',
        download: 'M19,9h-4V3H9v6H5l7,7L19,9zM5,18v2h14v-2H5z',
        github: 'M12,0c-6.63,0 -12,5.37 -12,12c0,5.3 3.44,9.8 8.21,11.39c0.6,0.11 0.82,-0.26 0.82,-0.58c0,-0.29 -0.01,-1.05 -0.02,-2.06c-3.34,0.73 -4.04,-1.61 -4.04,-1.61c-0.55,-1.39 -1.34,-1.76 -1.34,-1.76c-1.09,-0.74 0.08,-0.73 0.08,-0.73c1.21,0.09 1.84,1.24 1.84,1.24c1.07,1.83 2.81,1.3 3.5,1c0.11,-0.78 0.42,-1.31 0.76,-1.61c-2.66,-0.3 -5.47,-1.33 -5.47,-5.93c0,-1.31 0.47,-2.38 1.24,-3.22c-0.12,-0.3 -0.54,-1.52 0.12,-3.18c0,0 1.01,-0.32 3.3,1.23c0.96,-0.27 1.98,-0.4 3,-0.4c1.02,0 2.04,0.13 3,0.4c2.28,-1.55 3.29,-1.23 3.29,-1.23c0.66,1.66 0.24,2.88 0.12,3.18c0.77,0.84 1.23,1.91 1.23,3.22c0,4.61 -2.8,5.63 -5.48,5.92c0.42,0.37 0.81,1.1 0.81,2.22c0,1.61 -0.01,2.9 -0.01,3.29c0,0.32 0.22,0.7 0.83,0.58C20.56,21.79 24,17.29 24,12C24,5.37 18.63,0 12,0z',
        ellipsis: 'M6,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2s2,-0.9 2,-2S7.1,10 6,10zM18,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2s2,-0.9 2,-2S19.1,10 18,10zM12,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2s2,-0.9 2,-2S13.1,10 12,10z'
    };

    function icon(name, cls) {
        return '<svg' + (cls ? ' class="' + cls + '"' : '') + ' viewBox="0 0 24 24"><path d="' + ICONS[name] + '"/></svg>';
    }

    // ------------------------------------------------------------------
    // Small helpers
    // ------------------------------------------------------------------

    var state = {
        repo: null,
        files: [],
        ahead: 0,
        behind: 0,
        remotes: [],
        branches: [],
        commits: [],
        repos: [],
        identity: { name: '', email: '' },
        currentView: 'sc'
    };

    var busy = 0;

    function esc(s) {
        return String(s == null ? '' : s)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }

    function $(id) { return document.getElementById(id); }

    function toast(msg) {
        var t = $('toast');
        t.textContent = msg;
        t.classList.add('show');
        clearTimeout(toast._timer);
        toast._timer = setTimeout(function () { t.classList.remove('show'); }, 2500);
    }

    function setBusy(on) {
        busy = Math.max(0, busy + (on ? 1 : -1));
        $('progress').style.display = busy > 0 ? 'block' : 'none';
        if (!busy) { $('progress-msg').textContent = ''; $('progress-bar').style.width = '0%'; }
    }

    function setProgressMsg(msg) {
        if (msg) { $('progress-msg').textContent = msg; $('progress-bar').style.width = '40%'; }
    }

    function copyText(text) {
        function legacy() {
            var ta = document.createElement('textarea');
            ta.value = text;
            ta.style.position = 'fixed';
            ta.style.opacity = '0';
            document.body.appendChild(ta);
            ta.select();
            var ok = false;
            try { ok = document.execCommand('copy'); } catch (e) {}
            document.body.removeChild(ta);
            return ok;
        }
        if (navigator.clipboard && navigator.clipboard.writeText) {
            navigator.clipboard.writeText(text).then(function () {
                toast('Copied to clipboard');
            }, function () { legacy() && toast('Copied to clipboard'); });
        } else {
            legacy() && toast('Copied to clipboard');
        }
    }

    function fmtDate(ts) {
        if (!ts) return '';
        try { return new Date(ts * 1000).toLocaleString(); } catch (e) { return String(ts); }
    }

    function fmtShortDate(ts) {
        if (!ts) return '';
        try {
            var d = new Date(ts * 1000);
            return d.toLocaleDateString();
        } catch (e) { return String(ts); }
    }

    // ------------------------------------------------------------------
    // Modal
    // ------------------------------------------------------------------

    function modal(opts) {
        return new Promise(function (resolve) {
            var overlay = $('modal-overlay');
            $('modal-title').textContent = opts.title || '';
            var body = $('modal-body');
            body.innerHTML = '';
            if (opts.info) {
                var info = document.createElement('div');
                info.className = 'info';
                info.textContent = opts.info;
                body.appendChild(info);
            }
            var values = {};
            (opts.fields || []).forEach(function (f) {
                var wrap = document.createElement('div');
                wrap.className = f.type === 'checkbox' ? 'field check' : 'field';
                if (f.type !== 'checkbox') {
                    var label = document.createElement('label');
                    label.textContent = f.label || '';
                    wrap.appendChild(label);
                }
                var input;
                if (f.type === 'textarea') {
                    input = document.createElement('textarea');
                } else {
                    input = document.createElement('input');
                    input.type = f.type === 'password' ? 'password' : (f.type === 'checkbox' ? 'checkbox' : 'text');
                }
                if (f.type === 'checkbox') {
                    input.checked = !!f.value;
                    var clabel = document.createElement('label');
                    clabel.textContent = f.label || '';
                    wrap.appendChild(input);
                    wrap.appendChild(clabel);
                } else {
                    input.value = f.value || '';
                    wrap.appendChild(input);
                }
                body.appendChild(wrap);
                values[f.id] = input;
            });

            var actions = $('modal-actions');
            actions.innerHTML = '';
            function close() {
                overlay.classList.remove('show');
            }
            (opts.buttons || []).forEach(function (b, i) {
                var btn = document.createElement('button');
                btn.className = 'btn' + (b.primary ? ' primary' : '');
                btn.textContent = b.label;
                btn.addEventListener('click', function () {
                    close();
                    var result = { button: b.value != null ? b.value : b.label };
                    Object.keys(values).forEach(function (k) {
                        var v = values[k];
                        result[k] = v.type === 'checkbox' ? v.checked : v.value;
                    });
                    resolve(result);
                });
                actions.appendChild(btn);
            });
            overlay.classList.add('show');
            var first = body.querySelector('input:not([type=checkbox]), textarea');
            if (first) { setTimeout(function () { first.focus(); }, 50); }
        });
    }

    function confirmDlg(title, info, confirmLabel) {
        return modal({
            title: title,
            info: info,
            buttons: [
                { label: 'Cancel', value: false },
                { label: confirmLabel || 'OK', value: true, primary: true }
            ]
        }).then(function (r) { return r.button; });
    }

    // ------------------------------------------------------------------
    // Navigation
    // ------------------------------------------------------------------

    var TITLES = {
        sc: 'Source Control',
        branches: 'Branches',
        history: 'History',
        repos: 'Repositories',
        settings: 'Settings'
    };

    var ACTIONS = {};

    function switchView(name) {
        state.currentView = name;
        ['sc', 'branches', 'history', 'repos', 'settings'].forEach(function (v) {
            $('view-' + v).classList.toggle('active', v === name);
        });
        qsa('#activitybar button[data-view]', function (b) {
            b.classList.toggle('active', b.getAttribute('data-view') === name);
        });
        $('sidebar-title').textContent = TITLES[name];
        renderActions();
        if (name === 'sc') renderSC();
        else if (name === 'branches') loadBranches();
        else if (name === 'history') loadHistory();
        else if (name === 'repos') loadRepos();
        else if (name === 'settings') renderSettings();
    }

    function buildActivityBar() {
        var bar = $('activitybar');
        var defs = [
            { view: 'sc', icon: 'sourceControl', title: 'Source Control', badge: true },
            { view: 'branches', icon: 'branch', title: 'Branches' },
            { view: 'history', icon: 'history', title: 'History' },
            { view: 'repos', icon: 'folder', title: 'Repositories' },
            { view: 'settings', icon: 'gear', title: 'Settings' }
        ];
        defs.forEach(function (d) {
            var btn = document.createElement('button');
            btn.setAttribute('data-view', d.view);
            btn.title = d.title;
            btn.innerHTML = icon(d.icon);
            if (d.badge) {
                var b = document.createElement('span');
                b.className = 'badge';
                b.id = 'sc-badge';
                btn.appendChild(b);
            }
            btn.addEventListener('click', function () { switchView(d.view); });
            bar.appendChild(btn);
        });
        var spacer = document.createElement('div');
        spacer.className = 'spacer';
        bar.appendChild(spacer);
        var back = document.createElement('button');
        back.title = 'Back to editor';
        back.innerHTML = icon('code');
        back.addEventListener('click', function () { call('openVscode').catch(function () {}); });
        bar.appendChild(back);
    }

    function setActions(id, buttons) {
        ACTIONS[id] = buttons;
    }

    function renderActions() {
        var container = $('sidebar-actions');
        container.innerHTML = '';
        (ACTIONS[state.currentView] || []).forEach(function (a) {
            var btn = document.createElement('button');
            btn.title = a.title || a.label;
            if (a.icon) btn.innerHTML = icon(a.icon);
            else btn.textContent = a.label || '';
            btn.addEventListener('click', a.onClick);
            container.appendChild(btn);
        });
    }

    // ------------------------------------------------------------------
    // Load + status
    // ------------------------------------------------------------------

    function load(initial) {
        call('getInfo').then(function (info) {
            state.repo = info.repo || null;
            if (state.repo) {
                loadStatus();
            } else {
                loadRepos();
                switchView('repos');
            }
        }).catch(function (e) {
            toast('Error: ' + e.message);
            renderWelcome();
        });
    }

    function loadStatus() {
        if (!state.repo) return;
        call('status').then(function (data) {
            state.repo = data;
            state.files = data.files || [];
            state.ahead = data.ahead || 0;
            state.behind = data.behind || 0;
            state.remotes = data.remotes || [];
            updateBadge();
            if (state.currentView === 'sc') renderSC();
        }).catch(function (e) {
            toast('Error loading status: ' + e.message);
        });
    }

    function updateBadge() {
        var badge = $('sc-badge');
        var n = state.files.length;
        badge.textContent = n > 999 ? '999+' : String(n);
        badge.classList.toggle('show', n > 0);
    }

    function statusChar(f) {
        if (f.conflicted) return 'C';
        if (f.indexStatus && f.indexStatus !== ' ') return f.indexStatus;
        if (f.worktreeStatus && f.worktreeStatus !== ' ') return f.worktreeStatus;
        if (f.untracked) return 'U';
        return 'M';
    }

    function isStaged(f) {
        return !!(f.indexStatus && f.indexStatus !== ' ');
    }

    function stagedCount() {
        return state.files.filter(isStaged).length;
    }

    // ------------------------------------------------------------------
    // Source Control view
    // ------------------------------------------------------------------

    function renderSC() {
        var el = $('view-sc');
        if (!state.repo) { renderWelcome(el); return; }

        setActions('sc', [
            { icon: 'ellipsis', title: 'More actions...', onClick: openScMenu },
            { icon: 'refresh', title: 'Refresh', onClick: loadStatus }
        ]);

        var parts = [];

        // repo row
        var branchName = state.repo.currentBranch;
        if (!branchName) {
            if (state.repo.detachedHead && state.repo.headId) {
                branchName = String(state.repo.headId).slice(0, 7);
            } else {
                branchName = 'main';
            }
        }
        parts.push(
            '<div class="sc-repo-row">' +
                '<span class="branch" title="' + esc(state.repo.directory) + '">' + icon('branch') + '<span>' + esc(branchName) + '</span></span>' +
                '<span class="ahead-behind">' +
                    (state.ahead > 0 ? '&#8593;' + state.ahead + ' ' : '') +
                    (state.behind > 0 ? '&#8595;' + state.behind : '') +
                '</span>' +
                '<button class="sync-btn" title="Sync (pull + push)" id="sync-btn">' + icon('sync') + '</button>' +
            '</div>'
        );

        // commit box
        parts.push(
            '<div class="commit-box">' +
                '<textarea id="commit-msg" placeholder="Message (press Ctrl+Enter to commit)"></textarea>' +
                '<div class="commit-row">' +
                    '<label class="amend"><input type="checkbox" id="commit-amend">Amend</label>' +
                    '<button class="commit-btn" id="commit-btn">Commit</button>' +
                '</div>' +
            '</div>'
        );

        // staged section
        var staged = state.files.filter(isStaged);
        parts.push(sectionHeader('Staged Changes (' + staged.length + ')',
            staged.length ? '<button id="unstage-all">Unstage All</button>' : ''));
        parts.push(scList(staged, true));

        // changes section
        var changes = state.files.filter(function (f) { return !isStaged(f); });
        parts.push(sectionHeader('Changes (' + changes.length + ')',
            changes.length ? '<button id="stage-all">Stage All</button>' : ''));
        parts.push(scList(changes, false));

        if (!state.files.length) {
            parts.push('<div class="empty-hint">There are no changes yet in this repository.</div>');
        }

        el.innerHTML = parts.join('');

        bind($('commit-btn'), function () { doCommit(); });
        $('commit-msg').addEventListener('keydown', function (e) {
            if (e.ctrlKey && e.key === 'Enter') doCommit();
        });
        var stageAll = $('stage-all');
        if (stageAll) bind(stageAll, function () { doStageAll(); });
        var unstageAll = $('unstage-all');
        if (unstageAll) bind(unstageAll, function () { doUnstageAll(); });
        bind($('sync-btn'), function () { doSync(); });

        qsa('#view-sc .sc-item[data-path]', function (item) {
            var f = JSON.parse(item.getAttribute('data-json'));
            item.addEventListener('click', function (e) {
                if (e.target.closest('.row-actions')) return;
                openFileDiff(f, isStaged(f));
            });
        });
        qsa('#view-sc .act-stage', function (b) {
            bind(b, function () { doStage([b.getAttribute('data-path')]); });
        });
        qsa('#view-sc .act-unstage', function (b) {
            bind(b, function () { doUnstage([b.getAttribute('data-path')]); });
        });
        qsa('#view-sc .act-discard', function (b) {
            bind(b, function () { doDiscard([b.getAttribute('data-path')]); });
        });
    }

    function sectionHeader(text, actionHtml) {
        return '<div class="section-header"><span>' + esc(text) + '</span>' + (actionHtml || '') + '</div>';
    }

    function scList(files, staged) {
        if (!files.length) return '';
        var rows = files.map(function (f) {
            var ch = statusChar(f);
            var actions = staged
                ? actBtn('unstage', 'Unstage', f.path) + actBtn('discard', 'Discard changes', f.path)
                : actBtn('stage', 'Stage changes', f.path) + actBtn('discard', 'Discard changes', f.path);
            return '<div class="sc-item" data-path="' + esc(f.path) + '" data-json="' +
                esc(JSON.stringify(f)).replace(/"/g, '&quot;') + '">' +
                '<span class="status-badge ' + esc(ch) + '">' + esc(ch) + '</span>' +
                '<span class="file-name" title="' + esc(f.path) + '">' + esc(f.path) + '</span>' +
                '<span class="row-actions">' + actions + '</span>' +
            '</div>';
        }).join('');
        return '<div class="sc-list">' + rows + '</div>';
    }

    function actBtn(act, title, path) {
        return '<button class="act-' + act + '" data-path="' + esc(path) + '" title="' + title + '">' +
            icon(act === 'stage' ? 'plus' : (act === 'unstage' ? 'minus' : 'trash')) +
            '</button>';
    }

    function openScMenu() {
        var items = [
            { label: 'Sync Changes', fn: doSync },
            { label: 'Stage All Changes', fn: doStageAll },
            { label: 'Unstage All Changes', fn: doUnstageAll },
            { label: 'Discard All Changes', fn: doDiscardAll },
            { label: 'Pull', fn: doPull },
            { label: 'Push', fn: doPush },
            { label: 'Fetch', fn: doFetch }
        ];
        showMenu(items, 'sidebar-actions');
    }

    function showMenu(items, anchorId) {
        var anchor = $(anchorId);
        var menu = document.createElement('div');
        menu.style.cssText = 'position:absolute;top:32px;right:8px;background:#252526;border:1px solid #3c3c3c;z-index:120;min-width:180px;box-shadow:0 2px 8px rgba(0,0,0,.4);';
        items.forEach(function (it) {
            var btn = document.createElement('button');
            btn.textContent = it.label;
            btn.style.cssText = 'display:block;width:100%;text-align:left;padding:6px 12px;color:#ccc;';
            btn.addEventListener('mouseenter', function () { btn.style.background = '#2a2d2e'; });
            btn.addEventListener('mouseleave', function () { btn.style.background = 'transparent'; });
            btn.addEventListener('click', function () {
                menu.remove();
                it.fn();
            });
            menu.appendChild(btn);
        });
        document.body.appendChild(menu);
        function close(e) {
            if (!menu.contains(e.target)) menu.remove();
            document.removeEventListener('click', close);
        }
        setTimeout(function () { document.addEventListener('click', close); }, 0);
    }

    // ------------------------------------------------------------------
    // Git operations
    // ------------------------------------------------------------------

    function doCommit() {
        var msg = $('commit-msg').value.trim();
        if (!msg) { toast('Enter a commit message'); return; }
        setBusy(true);
        call('commit', { message: msg, amend: $('commit-amend').checked }).then(function () {
            toast('Committed');
            $('commit-msg').value = '';
            $('commit-amend').checked = false;
            loadStatus();
        }).catch(function (e) {
            toast('Commit failed: ' + e.message);
        }).finally(function () { setBusy(false); });
    }

    function doStage(paths) {
        setBusy(true);
        call('stage', { paths: paths }).then(loadStatus)
            .catch(function (e) { toast('Failed to stage: ' + e.message); })
            .finally(function () { setBusy(false); });
    }

    function doUnstage(paths) {
        setBusy(true);
        call('unstage', { paths: paths }).then(loadStatus)
            .catch(function (e) { toast('Failed to unstage: ' + e.message); })
            .finally(function () { setBusy(false); });
    }

    function doStageAll() {
        setBusy(true);
        call('stage', { all: true }).then(loadStatus)
            .catch(function (e) { toast('Failed to stage all: ' + e.message); })
            .finally(function () { setBusy(false); });
    }

    function doUnstageAll() {
        setBusy(true);
        call('unstage', { all: true }).then(loadStatus)
            .catch(function (e) { toast('Failed to unstage all: ' + e.message); })
            .finally(function () { setBusy(false); });
    }

    function doDiscard(paths) {
        confirmDlg('Discard changes', 'Discard all local changes to the selected files?', 'Discard')
            .then(function (ok) {
                if (!ok) return;
                setBusy(true);
                call('discard', { paths: paths }).then(loadStatus)
                    .catch(function (e) { toast('Failed to discard: ' + e.message); })
                    .finally(function () { setBusy(false); });
            });
    }

    function doDiscardAll() {
        confirmDlg('Discard all changes', 'Discard all local changes in this repository?', 'Discard all')
            .then(function (ok) {
                if (!ok) return;
                setBusy(true);
                call('discard', { all: true }).then(loadStatus)
                    .catch(function (e) { toast('Failed to discard: ' + e.message); })
                    .finally(function () { setBusy(false); });
            });
    }

    function doSync() {
        var remote = state.remotes && state.remotes.length ? state.remotes[0] : 'origin';
        setBusy(true);
        setProgressMsg('Pulling...');
        call('pull', { remote: remote }).then(function () {
            setProgressMsg('Pushing...');
            return call('push', { remote: remote });
        }).then(function () {
            toast('Synced');
            loadStatus();
        }).catch(function (e) {
            toast('Sync failed: ' + e.message);
        }).finally(function () {
            setBusy(false);
            setProgressMsg('');
        });
    }

    function doPull() {
        modal({
            title: 'Pull',
            fields: [
                { id: 'remote', label: 'Remote', value: state.remotes[0] || 'origin' },
                { id: 'branch', label: 'Branch (optional)', value: '' },
                { id: 'rebase', label: 'Rebase', type: 'checkbox' }
            ],
            buttons: [
                { label: 'Cancel', value: false },
                { label: 'Pull', value: true, primary: true }
            ]
        }).then(function (r) {
            if (!r.button) return;
            setBusy(true);
            call('pull', { remote: r.remote, branch: r.branch || undefined, rebase: r.rebase }).then(function () {
                toast('Pull complete');
                loadStatus();
            }).catch(function (e) {
                toast('Pull failed: ' + e.message);
            }).finally(function () { setBusy(false); });
        });
    }

    function doPush() {
        modal({
            title: 'Push',
            fields: [
                { id: 'remote', label: 'Remote', value: state.remotes[0] || 'origin' },
                { id: 'branch', label: 'Branch (optional)', value: '' },
                { id: 'force', label: 'Force push', type: 'checkbox' }
            ],
            buttons: [
                { label: 'Cancel', value: false },
                { label: 'Push', value: true, primary: true }
            ]
        }).then(function (r) {
            if (!r.button) return;
            setBusy(true);
            call('push', { remote: r.remote, branch: r.branch || undefined, force: r.force }).then(function () {
                toast('Push complete');
                loadStatus();
            }).catch(function (e) {
                toast('Push failed: ' + e.message);
            }).finally(function () { setBusy(false); });
        });
    }

    function doFetch() {
        modal({
            title: 'Fetch',
            fields: [{ id: 'remote', label: 'Remote', value: state.remotes[0] || 'origin' }],
            buttons: [
                { label: 'Cancel', value: false },
                { label: 'Fetch', value: true, primary: true }
            ]
        }).then(function (r) {
            if (!r.button) return;
            setBusy(true);
            call('fetch', { remote: r.remote }).then(function () {
                toast('Fetch complete');
                loadStatus();
            }).catch(function (e) {
                toast('Fetch failed: ' + e.message);
            }).finally(function () { setBusy(false); });
        });
    }

    // ------------------------------------------------------------------
    // Diff + editor
    // ------------------------------------------------------------------

    function openFileDiff(f, staged) {
        setBusy(true);
        call('diff', { path: f.path, staged: staged }).then(function (files) {
            renderDiff(files, f.path, null);
        }).catch(function (e) {
            toast('Failed to load diff: ' + e.message);
        }).finally(function () { setBusy(false); });
    }

    function renderDiff(files, title, metaHtml) {
        var body = $('editor-body');
        var tab = $('editor-tab');
        $('editor-tab-name').textContent = title || 'Diff';
        tab.style.display = 'flex';

        if (!files || !files.length) {
            body.innerHTML = '<div class="empty-hint">No changes.</div>';
            return;
        }

        var totalAdd = 0, totalDel = 0;
        files.forEach(function (f) { totalAdd += f.additions || 0; totalDel += f.deletions || 0; });

        var html = metaHtml || '';
        html += '<div class="diff-header"><div class="diff-title">' + esc(title || 'Diff') + '</div>' +
            '<div class="diff-stats"><span class="add">+' + totalAdd + '</span> <span class="del">-' + totalDel + '</span></div></div>';

        files.forEach(function (f) {
            html += '<div class="diff-file">' +
                '<div class="file-line">' + icon('compare') + '<span>' + esc(f.path) + '</span>' +
                '<span style="margin-left:auto;font-size:11px;color:#969696"><span style="color:#89d185">+' + (f.additions || 0) + '</span> <span style="color:#f48771">-' + (f.deletions || 0) + '</span></span>' +
                '</div>' + diffTable(f.diff) +
            '</div>';
        });

        body.innerHTML = html;
    }

    function diffTable(text) {
        if (!text) return '';
        var lines = text.split('\n');
        var oldLine = 0, newLine = 0;
        var rows = [];
        lines.forEach(function (line) {
            if (line.indexOf('diff --git') === 0 || line.indexOf('index ') === 0 ||
                line.indexOf('new file mode') === 0 || line.indexOf('deleted file mode') === 0 ||
                line.indexOf('old mode') === 0 || line.indexOf('new mode') === 0 ||
                line.indexOf('similarity index') === 0 || line.indexOf('rename ') === 0) {
                return;
            }
            if (line.indexOf('--- ') === 0 || line.indexOf('+++ ') === 0) return;

            var m = line.match(/^@@ -(\d+)(?:,\d+)? \+(\d+)(?:,\d+)? @@/);
            if (m) {
                oldLine = parseInt(m[1], 10) - 1;
                newLine = parseInt(m[2], 10) - 1;
                rows.push('<tr class="hunk"><td class="ln"></td><td class="code">' + esc(line) + '</td></tr>');
                return;
            }
            if (line.charAt(0) === '+') {
                newLine++;
                rows.push('<tr class="add"><td class="ln"></td><td class="ln"></td><td class="code"><span class="add-text">' + esc(line) + '</span></td></tr>');
                return;
            }
            if (line.charAt(0) === '-') {
                oldLine++;
                rows.push('<tr class="del"><td class="ln"></td><td class="ln"></td><td class="code"><span class="del-text">' + esc(line) + '</span></td></tr>');
                return;
            }
            oldLine++;
            newLine++;
            rows.push('<tr><td class="ln">' + oldLine + '</td><td class="ln" style="background:#1e1e1e">' + newLine + '</td><td class="code">' + esc(line) + '</td></tr>');
        });
        return '<table class="diff-table">' + rows.join('') + '</table>';
    }

    // ------------------------------------------------------------------
    // Branches
    // ------------------------------------------------------------------

    function loadBranches() {
        if (!state.repo) { $('view-branches').innerHTML = '<div class="empty-hint">No repository open.</div>'; return; }
        setActions('branches', [
            { icon: 'plus', title: 'Create branch', onClick: createBranchPrompt },
            { icon: 'refresh', title: 'Refresh', onClick: loadBranches }
        ]);
        setBusy(true);
        call('branches').then(function (branches) {
            state.branches = branches || [];
            renderBranches();
        }).catch(function (e) {
            toast('Failed to load branches: ' + e.message);
        }).finally(function () { setBusy(false); });
    }

    function renderBranches() {
        var el = $('view-branches');
        var parts = [];
        parts.push(sectionHeader('Local'));
        var local = state.branches.filter(function (b) { return !b.remote; });
        var remote = state.branches.filter(function (b) { return b.remote; });
        if (!local.length) parts.push('<div class="empty-hint">No local branches.</div>');
        local.forEach(function (b) {
            var cur = b.current;
            parts.push(
                '<div class="branch-item' + (cur ? ' current' : '') + '" data-branch="' + esc(b.name) + '">' +
                    '<span class="current-dot"></span>' +
                    '<span class="branch-name" title="' + esc(b.name) + '">' + esc(b.name) + '</span>' +
                    (b.tracking ? '<span class="tag">' + esc(b.tracking) + '</span>' : '') +
                    '<span class="row-actions">' +
                        (cur ? '' : '<button class="act-checkout" title="Checkout">' + icon('chevronRight') + '</button>') +
                        (cur ? '' : '<button class="act-delete" title="Delete branch">' + icon('trash') + '</button>') +
                    '</span>' +
                '</div>'
            );
        });
        parts.push(sectionHeader('Remote'));
        remote.forEach(function (b) {
            parts.push(
                '<div class="branch-item" data-branch="' + esc(b.name) + '">' +
                    '<span class="current-dot"></span>' +
                    '<span class="branch-name" title="' + esc(b.name) + '">' + esc(b.name) + '</span>' +
                    '<span class="row-actions"><button class="act-checkout" title="Checkout">' + icon('chevronRight') + '</button></span>' +
                '</div>'
            );
        });
        parts.push('<div class="pad"><button class="btn" id="checkout-commit-btn">Checkout commit...</button></div>');
        el.innerHTML = parts.join('');

        qsa('#view-branches .act-checkout', function (b) {
            bind(b, function () { doCheckout(b.closest('.branch-item').getAttribute('data-branch')); });
        });
        qsa('#view-branches .act-delete', function (b) {
            bind(b, function () { doDeleteBranch(b.closest('.branch-item').getAttribute('data-branch')); });
        });
        bind($('checkout-commit-btn'), checkoutCommitPrompt);
    }

    function createBranchPrompt() {
        modal({
            title: 'Create Branch',
            fields: [
                { id: 'name', label: 'Branch name', value: '' },
                { id: 'startPoint', label: 'Start point (optional)', value: '' }
            ],
            buttons: [
                { label: 'Cancel', value: false },
                { label: 'Create', value: true, primary: true }
            ]
        }).then(function (r) {
            if (!r.button) return;
            if (!r.name) { toast('Branch name required'); return; }
            setBusy(true);
            call('createBranch', { name: r.name, startPoint: r.startPoint || undefined }).then(function () {
                toast('Branch created');
                loadStatus();
                loadBranches();
            }).catch(function (e) {
                toast('Failed to create branch: ' + e.message);
            }).finally(function () { setBusy(false); });
        });
    }

    function doCheckout(name) {
        setBusy(true);
        call('checkout', { name: name }).then(function () {
            toast('Switched to ' + name);
            loadStatus();
            loadBranches();
        }).catch(function (e) {
            toast('Checkout failed: ' + e.message);
        }).finally(function () { setBusy(false); });
    }

    function doDeleteBranch(name) {
        confirmDlg('Delete branch', 'Delete branch "' + name + '"?', 'Delete')
            .then(function (ok) {
                if (!ok) return;
                setBusy(true);
                call('deleteBranch', { name: name, force: false }).then(function () {
                    toast('Branch deleted');
                    loadBranches();
                }).catch(function (e) {
                    toast('Failed to delete branch: ' + e.message);
                }).finally(function () { setBusy(false); });
            });
    }

    function checkoutCommitPrompt() {
        modal({
            title: 'Checkout Commit',
            fields: [{ id: 'commit', label: 'Commit hash', value: '' }],
            buttons: [
                { label: 'Cancel', value: false },
                { label: 'Checkout', value: true, primary: true }
            ]
        }).then(function (r) {
            if (!r.button) return;
            setBusy(true);
            call('checkoutCommit', { commit: r.commit }).then(function () {
                toast('Detached HEAD at ' + r.commit);
                loadStatus();
            }).catch(function (e) {
                toast('Checkout failed: ' + e.message);
            }).finally(function () { setBusy(false); });
        });
    }

    // ------------------------------------------------------------------
    // History
    // ------------------------------------------------------------------

    function loadHistory() {
        if (!state.repo) { $('view-history').innerHTML = '<div class="empty-hint">No repository open.</div>'; return; }
        setActions('history', [
            { icon: 'refresh', title: 'Refresh', onClick: loadHistory }
        ]);
        setBusy(true);
        call('log', { max: 100 }).then(function (commits) {
            state.commits = commits || [];
            renderHistory();
        }).catch(function (e) {
            toast('Failed to load history: ' + e.message);
        }).finally(function () { setBusy(false); });
    }

    function renderHistory() {
        var el = $('view-history');
        var parts = state.commits.map(function (c, i) {
            return '<div class="commit-item' + (i === 0 ? ' selected' : '') + '" data-commit="' + esc(c.id) + '">' +
                '<span class="commit-subject">' + esc(c.subject) + '</span>' +
                '<span class="commit-meta">' + esc(c.shortId) + ' · ' + esc(c.authorName) + ' · ' + esc(fmtShortDate(c.commitTime)) + '</span>' +
            '</div>';
        }).join('');
        el.innerHTML = parts || '<div class="empty-hint">No commits yet.</div>';
        qsa('#view-history .commit-item', function (item) {
            bind(item, function () {
                qsa('#view-history .commit-item.selected', function (c) { c.classList.remove('selected'); });
                item.classList.add('selected');
                showCommit(item.getAttribute('data-commit'));
            });
        });
        if (state.commits.length) showCommit(state.commits[0].id);
    }

    function showCommit(id) {
        setBusy(true);
        call('show', { commit: id }).then(function (files) {
            renderCommitDetail(id, files);
        }).catch(function (e) {
            toast('Failed to load commit: ' + e.message);
        }).finally(function () { setBusy(false); });
    }

    function renderCommitDetail(id, files) {
        var commit = state.commits.find(function (c) { return c.id === id; });
        var body = $('editor-body');
        var tab = $('editor-tab');
        $('editor-tab-name').textContent = (commit ? commit.shortId : id) + ' — Commit';
        tab.style.display = 'flex';

        var meta = '';
        if (commit) {
            meta = '<div class="commit-detail">' +
                '<div class="cd-subject">' + esc(commit.subject) + '</div>' +
                '<div class="cd-meta">' + esc(commit.shortId) + ' · ' + esc(commit.authorName) + ' &lt;' + esc(commit.authorEmail) + '&gt; · ' + esc(fmtDate(commit.commitTime)) + '</div>' +
                '<div class="cd-meta" style="white-space:pre-wrap">' + esc(commit.message) + '</div>' +
            '</div>';
        }
        renderDiff(files, (commit ? commit.subject : id), meta);
    }

    // ------------------------------------------------------------------
    // Repositories
    // ------------------------------------------------------------------

    function loadRepos() {
        setActions('repos', [
            { icon: 'plus', title: 'More actions...', onClick: openRepoMenu },
            { icon: 'refresh', title: 'Refresh', onClick: loadRepos }
        ]);
        call('listRepos').then(function (data) {
            state.repos = data.repos || [];
            renderRepos();
        }).catch(function (e) {
            toast('Failed to list repos: ' + e.message);
        });
    }

    function renderRepos() {
        var el = $('view-repos');
        var parts = [];
        parts.push(sectionHeader('Open Recent'));
        if (!state.repos.length) {
            parts.push('<div class="empty-hint">No repositories found yet.<br>Open, clone or initialize one below.</div>');
        }
        state.repos.forEach(function (r) {
            var active = state.repo && state.repo.directory === r.path;
            parts.push(
                '<div class="repo-item' + (active ? ' active-repo' : '') + '" data-path="' + esc(r.path) + '">' +
                    '<span class="repo-name">' + esc(r.name) + '</span>' +
                    '<span class="repo-path">' + esc(r.path) + '</span>' +
                '</div>'
            );
        });
        parts.push(
            '<div class="quick-actions">' +
                '<button id="act-open-repo"><span>&#128194; Open repository...</span><div class="hint">Browse the filesystem for an existing Git repository</div></button>' +
                '<button id="act-clone-repo"><span>&#8681; Clone repository...</span><div class="hint">Clone a remote repository into Repositories</div></button>' +
                '<button id="act-init-repo"><span>&#10010; Initialize repository...</span><div class="hint">Create a new repository in an empty folder</div></button>' +
            '</div>'
        );
        el.innerHTML = parts.join('');
        qsa('#view-repos .repo-item', function (item) {
            bind(item, function () { openRepo(item.getAttribute('data-path')); });
        });
        bind($('act-open-repo'), function () { pickDir('open'); });
        bind($('act-clone-repo'), clonePrompt);
        bind($('act-init-repo'), function () { pickDir('init'); });
    }

    function openRepoMenu() {
        showMenu([
            { label: 'Open repository...', fn: function () { pickDir('open'); } },
            { label: 'Clone repository...', fn: clonePrompt },
            { label: 'Initialize repository...', fn: function () { pickDir('init'); } },
            { label: 'Refresh', fn: loadRepos }
        ], 'sidebar-actions');
    }

    function openRepo(path) {
        setBusy(true);
        call('setActiveRepo', { dir: path }).then(function (repo) {
            state.repo = repo;
            loadStatus();
            switchView('sc');
        }).catch(function (e) {
            toast('Failed to open repository: ' + e.message);
        }).finally(function () { setBusy(false); });
    }

    function pickDir(mode) {
        call('openRepoDir').catch(function (e) { toast('Failed to open picker: ' + e.message); });
        pendingDirMode = mode;
    }

    var pendingDirMode = 'open';

    function handleDirPicked(d) {
        if (d.isRepo) {
            openRepo(d.path);
            return;
        }
        if (pendingDirMode === 'init') {
            modal({
                title: 'Initialize Repository',
                info: d.path,
                fields: [],
                buttons: [
                    { label: 'Cancel', value: false },
                    { label: 'Initialize', value: true, primary: true }
                ]
            }).then(function (r) {
                if (!r.button) return;
                setBusy(true);
                call('init', { dir: d.path }).then(function () {
                    toast('Repository initialized');
                    state.repo = d.path;
                    loadStatus();
                    switchView('sc');
                }).catch(function (e) {
                    toast('Failed to initialize: ' + e.message);
                }).finally(function () { setBusy(false); });
            });
        } else {
            modal({
                title: 'Not a Git repository',
                info: d.path + '\n\nThis folder is not a Git repository.',
                fields: [],
                buttons: [
                    { label: 'Cancel', value: false },
                    { label: 'Initialize repository here', value: 'init', primary: true },
                    { label: 'Choose another folder', value: 'pick' }
                ]
            }).then(function (r) {
                if (!r.button) return;
                if (r.button === 'init') { pendingDirMode = 'init'; handleDirPicked(d); }
                else pickDir('open');
            });
        }
    }

    function clonePrompt() {
        modal({
            title: 'Clone Repository',
            fields: [
                { id: 'url', label: 'Repository URL', value: '' },
                { id: 'dir', label: 'Destination (optional)', value: '' },
                { id: 'recursive', label: 'Clone submodules (recursive)', type: 'checkbox' }
            ],
            buttons: [
                { label: 'Cancel', value: false },
                { label: 'Clone', value: true, primary: true }
            ]
        }).then(function (r) {
            if (!r.button) return;
            if (!r.url) { toast('Repository URL required'); return; }
            setBusy(true);
            call('clone', { url: r.url, dir: r.dir || undefined, recursive: r.recursive, setActive: true }).then(function () {
                toast('Clone complete');
                state.repo = {};
                loadStatus();
                switchView('sc');
            }).catch(function (e) {
                toast('Clone failed: ' + e.message);
            }).finally(function () { setBusy(false); });
        });
    }

    // ------------------------------------------------------------------
    // Settings
    // ------------------------------------------------------------------

    function renderSettings() {
        var el = $('view-settings');
        setActions('settings', []);
        Promise.all([call('getIdentity'), call('getInfo')]).then(function (res) {
            var identity = res[0] || {};
            var info = res[1] || {};
            var hasRepo = !!info.repo;
            var configPromise = hasRepo
                ? Promise.all([call('configGet', { key: 'user.name' }), call('configGet', { key: 'user.email' })])
                : Promise.resolve([null, null]);
            return configPromise.then(function (cfg) {
                return { identity: identity, info: info, cfg: cfg };
            });
        }).then(function (data) {
            renderSettingsInner(el, data);
        }).catch(function (e) {
            el.innerHTML = '<div class="empty-hint">' + esc(e.message) + '</div>';
        });
    }

    function renderSettingsInner(el, data) {
        var parts = [];
        var identity = data.identity;
        state.identity = identity;

        parts.push('<div class="settings-section"><div class="ss-title">Identity</div>' +
            '<div class="field"><label>Name</label><input type="text" id="set-name" value="' + esc(identity.name || '') + '"></div>' +
            '<div class="field"><label>Email</label><input type="text" id="set-email" value="' + esc(identity.email || '') + '"></div>' +
            '<div class="field"><label></label><button class="btn primary" id="set-identity-save">Save Identity</button></div>' +
        '</div>');

        parts.push('<div class="settings-section"><div class="ss-title">Repository Config</div>');
        if (data.cfg) {
            parts.push('<div class="field"><label>user.name</label><input type="text" id="cfg-name" value="' + esc(data.cfg[0] || '') + '"></div>' +
                '<div class="field"><label>user.email</label><input type="text" id="cfg-email" value="' + esc(data.cfg[1] || '') + '"></div>' +
                '<div class="field"><label></label><button class="btn primary" id="set-config-save">Save to Repo</button></div>');
        } else {
            parts.push('<div class="empty-hint">Open a repository to edit its config.</div>');
        }
        parts.push('</div>');

        parts.push('<div class="settings-section"><div class="ss-title">Credentials (HTTPS)</div>' +
            '<div class="field"><label>URL</label><input type="text" id="cred-url" placeholder="https://github.com/user/repo.git"></div>' +
            '<div class="field"><label>Username</label><input type="text" id="cred-user"></div>' +
            '<div class="field"><label>Password / Token</label><input type="password" id="cred-pass"></div>' +
            '<div class="field"><label></label>' +
                '<button class="btn primary" id="cred-save">Save</button>' +
                '<button class="btn danger" id="cred-delete">Delete</button>' +
            '</div>' +
        '</div>');

        parts.push('<div class="settings-section"><div class="ss-title">SSH Keys</div>' +
            '<div id="ssh-list"><div class="empty-hint">Loading...</div></div>' +
            '<div class="field" style="margin-top:8px"><label></label><button class="btn primary" id="ssh-generate">Generate SSH Key</button></div>' +
        '</div>');

        el.innerHTML = parts.join('');

        bind($('set-identity-save'), function () { saveIdentity(); });
        if (data.cfg) bind($('set-config-save'), function () { saveConfig(); });
        bind($('cred-save'), saveCredential);
        bind($('cred-delete'), deleteCredential);
        bind($('ssh-generate'), generateSshKeyPrompt);
        loadSshKeys();
    }

    function saveIdentity() {
        setBusy(true);
        call('setIdentity', {
            name: $('set-name').value,
            email: $('set-email').value
        }).then(function () {
            toast('Identity saved');
        }).catch(function (e) {
            toast('Failed: ' + e.message);
        }).finally(function () { setBusy(false); });
    }

    function saveConfig() {
        setBusy(true);
        call('configSet', { key: 'user.name', value: $('cfg-name').value }).then(function () {
            return call('configSet', { key: 'user.email', value: $('cfg-email').value });
        }).then(function () {
            toast('Config saved');
        }).catch(function (e) {
            toast('Failed: ' + e.message);
        }).finally(function () { setBusy(false); });
    }

    function saveCredential() {
        var url = $('cred-url').value.trim();
        if (!url) { toast('Enter a repository URL'); return; }
        setBusy(true);
        call('saveCredentials', {
            url: url,
            username: $('cred-user').value,
            password: $('cred-pass').value
        }).then(function () {
            toast('Credentials saved');
            $('cred-pass').value = '';
        }).catch(function (e) {
            toast('Failed: ' + e.message);
        }).finally(function () { setBusy(false); });
    }

    function deleteCredential() {
        var url = $('cred-url').value.trim();
        if (!url) { toast('Enter a repository URL'); return; }
        confirmDlg('Delete credentials', 'Remove saved credentials for ' + url + '?', 'Delete')
            .then(function (ok) {
                if (!ok) return;
                setBusy(true);
                call('deleteCredentials', { url: url }).then(function () {
                    toast('Credentials deleted');
                }).catch(function (e) {
                    toast('Failed: ' + e.message);
                }).finally(function () { setBusy(false); });
            });
    }

    function loadSshKeys() {
        call('listSshKeys').then(function (keys) {
            var list = $('ssh-list');
            if (!list) return;
            if (!keys || !keys.length) {
                list.innerHTML = '<div class="empty-hint">No SSH keys yet.</div>';
                return;
            }
            list.innerHTML = keys.map(function (k) {
                return '<div class="ssh-item">' +
                    '<span class="ssh-name" title="' + esc(k.fingerprint || '') + '">' + esc(k.name) + '</span>' +
                    '<span class="row-actions">' +
                        '<button class="ssh-copy" title="Copy public key">' + icon('copy') + '</button>' +
                        '<button class="ssh-delete" title="Delete key">' + icon('trash') + '</button>' +
                    '</span>' +
                '</div>';
            }).join('');
            Array.prototype.forEach.call(list.querySelectorAll('.ssh-copy'), function (b) {
                bind(b, function () { copySshKey(b.closest('.ssh-item').querySelector('.ssh-name').textContent); });
            });
            Array.prototype.forEach.call(list.querySelectorAll('.ssh-delete'), function (b) {
                bind(b, function () { deleteSshKey(b.closest('.ssh-item').querySelector('.ssh-name').textContent); });
            });
        }).catch(function (e) {
            var list = $('ssh-list');
            if (list) list.innerHTML = '<div class="empty-hint">' + esc(e.message) + '</div>';
        });
    }

    function copySshKey(name) {
        call('getSshPublicKey', { name: name }).then(function (data) {
            copyText(data.content || '');
        }).catch(function (e) {
            toast('Failed: ' + e.message);
        });
    }

    function deleteSshKey(name) {
        confirmDlg('Delete SSH key', 'Delete SSH key "' + name + '"?', 'Delete')
            .then(function (ok) {
                if (!ok) return;
                setBusy(true);
                call('deleteSshKey', { name: name }).then(function () {
                    toast('Key deleted');
                    loadSshKeys();
                }).catch(function (e) {
                    toast('Failed: ' + e.message);
                }).finally(function () { setBusy(false); });
            });
    }

    function generateSshKeyPrompt() {
        modal({
            title: 'Generate SSH Key',
            fields: [
                { id: 'comment', label: 'Comment (email)', value: state.identity.email || '' },
                { id: 'passphrase', label: 'Passphrase (optional)', type: 'password', value: '' }
            ],
            buttons: [
                { label: 'Cancel', value: false },
                { label: 'Generate', value: true, primary: true }
            ]
        }).then(function (r) {
            if (!r.button) return;
            setBusy(true);
            call('generateSshKey', { comment: r.comment || 'android', passphrase: r.passphrase || '' }).then(function (key) {
                toast('SSH key generated');
                copySshKey(key.name);
                loadSshKeys();
            }).catch(function (e) {
                toast('Failed to generate key: ' + e.message);
            }).finally(function () { setBusy(false); });
        });
    }

    // ------------------------------------------------------------------
    // Credential + progress events
    // ------------------------------------------------------------------

    function promptCredentials(url) {
        modal({
            title: 'Sign in required',
            info: url || '',
            fields: [
                { id: 'username', label: 'Username', value: '' },
                { id: 'password', label: 'Password / Token', type: 'password', value: '' },
                { id: 'save', label: 'Save credentials', type: 'checkbox', value: true }
            ],
            buttons: [
                { label: 'Cancel', value: false },
                { label: 'Sign in', value: true, primary: true }
            ]
        }).then(function (r) {
            if (!r.button) return;
            setBusy(true);
            call('saveCredentials', { url: url || '', username: r.username, password: r.password }).then(function () {
                if (state.repo) loadStatus();
            }).catch(function (e) {
                toast('Failed: ' + e.message);
            }).finally(function () { setBusy(false); });
        });
    }

    function promptSshPassphrase(url) {
        modal({
            title: 'SSH key passphrase required',
            info: url || '',
            fields: [
                { id: 'passphrase', label: 'Passphrase', type: 'password', value: '' },
                { id: 'save', label: 'Save passphrase', type: 'checkbox', value: true }
            ],
            buttons: [
                { label: 'Cancel', value: false },
                { label: 'OK', value: true, primary: true }
            ]
        }).then(function (r) {
            if (!r.button) return;
            setBusy(true);
            call('saveSshPassphrase', { passphrase: r.passphrase }).then(function () {
                if (state.repo) loadStatus();
            }).catch(function (e) {
                toast('Failed: ' + e.message);
            }).finally(function () { setBusy(false); });
        });
    }

    // ------------------------------------------------------------------
    // Welcome (no repo)
    // ------------------------------------------------------------------

    function renderWelcome() {
        $('view-sc').innerHTML =
            '<div class="empty-hint">' +
            '<div style="font-size:20px;color:#4f4f4f">' + icon('sourceControl') + '</div>' +
            'Open a repository to view its Source Control.' +
            '</div>';
    }

    // ------------------------------------------------------------------
    // Event wiring
    // ------------------------------------------------------------------

    function bind(el, fn) {
        if (el) el.addEventListener('click', fn);
    }

    function on(name, handler) {
        document.addEventListener('median-git:' + name, function (e) { handler(e.detail || {}); });
    }

    on('status-changed', function () {
        if (state.repo) { loadStatus(); loadRepos(); }
        else load();
    });
    on('progress', function (d) {
        if (d.message) setProgressMsg(d.message);
    });
    on('credentials-required', function (d) { promptCredentials(d.url); });
    on('ssh-passphrase-required', function (d) { promptSshPassphrase(d.url); });
    on('directory-picked', function (d) { handleDirPicked(d); });

    bind($('editor-close'), function () {
        $('editor-tab').style.display = 'none';
        $('editor-body').innerHTML = '';
    });

    // ------------------------------------------------------------------
    // Init
    // ------------------------------------------------------------------

    buildActivityBar();
    switchView('sc');
    load(true);
})();
