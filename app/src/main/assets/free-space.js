(function(points) {
    'use strict';
    var viewport = window.visualViewport;
    function free(point) {
        var x = point[0] * (viewport ? viewport.width : innerWidth) + (viewport ? viewport.offsetLeft : 0);
        var y = point[1] * (viewport ? viewport.height : innerHeight) + (viewport ? viewport.offsetTop : 0);
        var element = document.elementFromPoint(x, y);
        if (!element) return false;
        // Keep page controls, media, links and editable or selectable text usable.
        for (var parent = element; parent; parent = parent.parentElement) {
            if (parent.matches('a,button,input,textarea,select,option,label,summary,iframe,frame,video,audio,canvas,img,svg,object,embed,[onclick],[ondblclick],[tabindex],[role="button"],[role="link"],[role="slider"],[role="textbox"],[role="checkbox"],[role="switch"],[role="menuitem"]') || parent.isContentEditable || parent.draggable || parent.shadowRoot) return false;
            var style = getComputedStyle(parent);
            if (style.cursor === 'pointer' || style.touchAction === 'none') return false;
        }
        var selection = getSelection();
        if (selection && !selection.isCollapsed) return false;
        var caret = document.caretRangeFromPoint ? document.caretRangeFromPoint(x, y) : null;
        if (caret && caret.startContainer.nodeType === 3) {
            var text = caret.startContainer, offset = caret.startOffset;
            for (var i = Math.max(0, offset - 1); i < Math.min(text.length, offset + 1); i++) {
                if (!/\S/.test(text.data.charAt(i))) continue;
                var range = document.createRange();range.setStart(text, i);range.setEnd(text, i + 1);
                var rects = range.getClientRects();
                for (var j = 0; j < rects.length; j++) {
                    var rect = rects[j];
                    if (x >= rect.left && x <= rect.right && y >= rect.top && y <= rect.bottom) return false;
                }
            }
        }
        return true;
    }
    return points.every(free);
})
