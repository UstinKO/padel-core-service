/**
 * bracket-zoom-export.js (LFPT-389)
 * Масштабирование/pan турнирной сетки Team Playoff (desktop fit-to-width,
 * mobile pinch-zoom+pan) и экспорт всей сетки в PNG через SnapDOM.
 *
 * Подключить в: templates/tournaments/team-playoff/view.html
 * Использование: BracketZoomExport.init({ tournamentId: 123 });
 */
(function (window, document) {
    'use strict';

    var MOBILE_BREAKPOINT = 768;
    var MIN_DESKTOP_SCALE = 0.75;
    var MIN_SCALE = 0.15;
    var MAX_SCALE = 2.0;
    var ZOOM_STEP = 0.1;
    var RESIZE_DEBOUNCE_MS = 150;

    function BracketZoomExport() {}

    BracketZoomExport.init = function (options) {
        var viewport = document.getElementById('bracketViewport');
        var stage = document.getElementById('bracketStage');
        var content = document.getElementById('bracketContent');
        if (!viewport || !stage || !content) {
            return; // плей-офф ещё не начат — секция не отрендерена
        }

        var tournamentId = options && options.tournamentId;
        var scale = 1;
        var panX = 0;
        var panY = 0;
        var userAdjustedZoom = false;

        function isMobile() {
            return window.innerWidth <= MOBILE_BREAKPOINT;
        }

        function naturalSize() {
            // Размер контента не зависит от применённого transform (transform не влияет на layout box).
            return { width: content.scrollWidth, height: content.scrollHeight };
        }

        function clamp(value, min, max) {
            return Math.min(max, Math.max(min, value));
        }

        function clampPan() {
            var natural = naturalSize();
            var viewW = viewport.clientWidth;
            var viewH = viewport.clientHeight;
            var contentW = natural.width * scale;
            var contentH = natural.height * scale;

            if (contentW <= viewW) {
                panX = (viewW - contentW) / 2;
            } else {
                panX = clamp(panX, viewW - contentW, 0);
            }
            if (contentH <= viewH) {
                panY = (viewH - contentH) / 2;
            } else {
                panY = clamp(panY, viewH - contentH, 0);
            }
        }

        function applyTransform() {
            clampPan();
            stage.style.transform = 'translate(' + panX + 'px,' + panY + 'px) scale(' + scale + ')';

            if (!isMobile()) {
                // На десктопе высота viewport подстраивается под текущий масштаб,
                // чтобы под уменьшенной сеткой не оставалось пустого места.
                viewport.style.height = Math.ceil(naturalSize().height * scale) + 'px';
            }

            var natural = naturalSize();
            var overflowing = natural.width * scale > viewport.clientWidth + 1
                || natural.height * scale > viewport.clientHeight + 1;
            viewport.classList.toggle('grabbable', overflowing);
        }

        function computeFitScale(constrainHeight) {
            var natural = naturalSize();
            if (natural.width === 0) {
                return 1;
            }
            var viewW = viewport.clientWidth || natural.width;
            var fitW = viewW / natural.width;
            var fit = fitW;
            if (constrainHeight) {
                var viewH = viewport.clientHeight || natural.height;
                fit = Math.min(fitW, viewH / natural.height);
            }
            return Math.min(1, fit);
        }

        function applyFitScale(recenter) {
            var fit = computeFitScale(isMobile());
            if (!isMobile() && fit < MIN_DESKTOP_SCALE) {
                fit = MIN_DESKTOP_SCALE;
            }
            scale = fit;
            if (recenter) {
                panX = 0;
                panY = 0;
            }
            applyTransform();
        }

        function zoomAt(newScale, clientX, clientY) {
            var rect = viewport.getBoundingClientRect();
            var originX = clientX - rect.left;
            var originY = clientY - rect.top;
            var stageX = (originX - panX) / scale;
            var stageY = (originY - panY) / scale;

            scale = clamp(newScale, MIN_SCALE, MAX_SCALE);
            panX = originX - stageX * scale;
            panY = originY - stageY * scale;
            applyTransform();
        }

        function zoomAtCenter(newScale) {
            var rect = viewport.getBoundingClientRect();
            zoomAt(newScale, rect.left + rect.width / 2, rect.top + rect.height / 2);
        }

        // ══ Кнопки управления ══
        var btnOut = document.getElementById('bracketZoomOut');
        var btnReset = document.getElementById('bracketZoomReset');
        var btnIn = document.getElementById('bracketZoomIn');
        var btnFit = document.getElementById('bracketFitScreen');
        var btnDownload = document.getElementById('bracketDownload');

        if (btnOut) {
            btnOut.addEventListener('click', function () {
                userAdjustedZoom = true;
                zoomAtCenter(scale - ZOOM_STEP);
            });
        }
        if (btnIn) {
            btnIn.addEventListener('click', function () {
                userAdjustedZoom = true;
                zoomAtCenter(scale + ZOOM_STEP);
            });
        }
        if (btnReset) {
            btnReset.addEventListener('click', function () {
                userAdjustedZoom = true;
                zoomAtCenter(1);
            });
        }
        if (btnFit) {
            btnFit.addEventListener('click', function () {
                userAdjustedZoom = false;
                applyFitScale(true);
            });
        }

        // ══ Мышь: drag-pan (десктоп, когда сетка не помещается целиком) ══
        var dragging = false;
        var dragStartX = 0;
        var dragStartY = 0;
        var dragPanStartX = 0;
        var dragPanStartY = 0;

        viewport.addEventListener('mousedown', function (e) {
            if (e.button !== 0) return;
            dragging = true;
            dragStartX = e.clientX;
            dragStartY = e.clientY;
            dragPanStartX = panX;
            dragPanStartY = panY;
            viewport.classList.add('grabbing');
        });
        window.addEventListener('mousemove', function (e) {
            if (!dragging) return;
            panX = dragPanStartX + (e.clientX - dragStartX);
            panY = dragPanStartY + (e.clientY - dragStartY);
            applyTransform();
        });
        window.addEventListener('mouseup', function () {
            dragging = false;
            viewport.classList.remove('grabbing');
        });

        // ══ Touch: pinch-zoom + pan (мобильный) ══
        function touchDistance(touches) {
            var dx = touches[0].clientX - touches[1].clientX;
            var dy = touches[0].clientY - touches[1].clientY;
            return Math.sqrt(dx * dx + dy * dy);
        }
        function touchMidpoint(touches) {
            return {
                x: (touches[0].clientX + touches[1].clientX) / 2,
                y: (touches[0].clientY + touches[1].clientY) / 2
            };
        }

        var pinchStartDistance = 0;
        var pinchStartScale = 1;
        var singleTouchStartX = 0;
        var singleTouchStartY = 0;
        var singleTouchPanStartX = 0;
        var singleTouchPanStartY = 0;
        var activeMode = null; // 'pinch' | 'pan'

        viewport.addEventListener('touchstart', function (e) {
            userAdjustedZoom = true;
            if (e.touches.length === 2) {
                activeMode = 'pinch';
                pinchStartDistance = touchDistance(e.touches);
                pinchStartScale = scale;
            } else if (e.touches.length === 1) {
                activeMode = 'pan';
                singleTouchStartX = e.touches[0].clientX;
                singleTouchStartY = e.touches[0].clientY;
                singleTouchPanStartX = panX;
                singleTouchPanStartY = panY;
            }
        }, { passive: true });

        viewport.addEventListener('touchmove', function (e) {
            if (activeMode === 'pinch' && e.touches.length === 2) {
                e.preventDefault();
                var newDistance = touchDistance(e.touches);
                var mid = touchMidpoint(e.touches);
                var ratio = pinchStartDistance > 0 ? newDistance / pinchStartDistance : 1;
                zoomAt(pinchStartScale * ratio, mid.x, mid.y);
            } else if (activeMode === 'pan' && e.touches.length === 1) {
                e.preventDefault();
                panX = singleTouchPanStartX + (e.touches[0].clientX - singleTouchStartX);
                panY = singleTouchPanStartY + (e.touches[0].clientY - singleTouchStartY);
                applyTransform();
            }
        }, { passive: false });

        viewport.addEventListener('touchend', function (e) {
            if (e.touches.length === 0) {
                activeMode = null;
            } else if (e.touches.length === 1) {
                // осталась одна точка после снятия второй — переходим в режим pan без скачка
                activeMode = 'pan';
                singleTouchStartX = e.touches[0].clientX;
                singleTouchStartY = e.touches[0].clientY;
                singleTouchPanStartX = panX;
                singleTouchPanStartY = panY;
            }
        }, { passive: true });

        // ══ Resize / поворот экрана ══
        var resizeTimer = null;
        window.addEventListener('resize', function () {
            clearTimeout(resizeTimer);
            resizeTimer = setTimeout(function () {
                if (!userAdjustedZoom) {
                    applyFitScale(true);
                } else {
                    applyTransform(); // не сбрасываем масштаб, только не даём сетке уйти за экран
                }
            }, RESIZE_DEBOUNCE_MS);
        });

        // ══ Экспорт в PNG (SnapDOM) ══
        if (btnDownload) {
            btnDownload.addEventListener('click', function () {
                if (!window.snapdom) {
                    return;
                }
                var exportHeader = document.getElementById('bracketExportHeader');
                var exportSlot = document.getElementById('bracketExportSlot');
                if (!exportHeader || !exportSlot) {
                    return;
                }

                var clone = content.cloneNode(true);
                clone.removeAttribute('id');
                exportSlot.innerHTML = '';
                exportSlot.appendChild(clone);

                var originalLabel = btnDownload.innerHTML;
                btnDownload.disabled = true;

                var filename = 'bracket-team-playoff-' + (tournamentId != null ? tournamentId : 'export');
                window.snapdom.download(exportHeader, { format: 'png', filename: filename })
                    .catch(function (err) {
                        if (window.console && console.error) {
                            console.error('Bracket PNG export failed', err);
                        }
                    })
                    .then(function () {
                        exportSlot.innerHTML = '';
                        btnDownload.disabled = false;
                        btnDownload.innerHTML = originalLabel;
                    });
            });
        }

        // ══ Инициализация ══
        applyFitScale(true);
    };

    window.BracketZoomExport = BracketZoomExport;
})(window, document);
