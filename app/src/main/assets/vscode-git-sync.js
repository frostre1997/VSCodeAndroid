(function () {
    'use strict';

    // Keeps the Source Control badge on vscode.dev's activity bar in sync
    // with the native Git engine. Runs inside vscode.dev; GitBridge is
    // always available on the main WebView.

    function qsa(sel) {
        return Array.prototype.slice.call(document.querySelectorAll(sel));
    }

    function findSCItem() {
        var items = qsa('[title="Source Control"], .monaco-workbench .activitybar [aria-label="Source Control"], .monaco-workbench [aria-label="Source Control"]');
        return items[0] || null;
    }

    function setBadge(item, count) {
        if (!item) return;
        var badge = item.querySelector('.sc-sync-badge');
        if (!badge) {
            badge = document.createElement('span');
            badge.className = 'sc-sync-badge';
            badge.style.cssText = 'position:absolute;top:4px;right:6px;min-width:14px;height:14px;padding:0 3px;border-radius:7px;background:#007acc;color:#fff;font:9px/14px sans-serif;text-align:center;pointer-events:none;z-index:10;';
            item.style.position = 'relative';
            item.appendChild(badge);
        }
        badge.textContent = count > 999 ? '999+' : String(count);
        badge.style.display = count > 0 ? 'block' : 'none';
    }

    function refresh() {
        var bridge = window.GitBridge;
        if (!bridge || typeof bridge.invoke !== 'function') return;
        var cb = '_sc_sync_' + Math.random().toString(36).slice(2);
        window[cb] = function (r) {
            delete window[cb];
            var count = 0;
            if (r && r.success && r.data && r.data.files) {
                count = r.data.files.length;
            }
            setBadge(findSCItem(), count);
        };
        try {
            bridge.invoke('status', '{}', cb);
        } catch (e) {
            delete window[cb];
        }
    }

    function start() {
        refresh();
        document.addEventListener('median-git:status-changed', function () {
            refresh();
        });
        setInterval(refresh, 15000);
        document.addEventListener('visibilitychange', function () {
            if (!document.hidden) refresh();
        });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', start);
    } else {
        start();
    }
})();
