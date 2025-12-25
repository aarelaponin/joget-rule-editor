/**
 * JRE Editor - CodeMirror-based editor for Rules Script (Joget Rule Editor)
 *
 * Usage:
 *   var editor = JREEditor.init('container-id', {
 *       apiBase: '/jw/api/erel/rules',
 *       apiId: 'API-xxx',
 *       apiKey: 'xxx',
 *       scopeCode: 'FARMER_ELIGIBILITY',
 *       contextType: 'ELIGIBILITY',
 *       contextCode: 'PROG-2025-001',
 *       hiddenFieldId: 'eligibilityScript',
 *       onValidate: function(result) { ... },
 *       onSave: function(result) { ... }
 *   });
 */
var JREEditor = (function() {
    'use strict';

    var defaults = {
        apiBase: '/jw/api/erel/rules',
        apiId: '',
        apiKey: '',
        scopeCode: 'FARMER_ELIGIBILITY',
        contextType: 'ELIGIBILITY',
        contextCode: '',
        rulesetCode: '',
        hiddenFieldId: 'eligibilityScript',
        rulesetCodeFieldId: '',
        height: 'auto',
        showDictionary: true,
        showSaveButton: true,
        onValidate: null,
        onSave: null,
        onError: null
    };

    // Field dictionary cache
    var fieldDictionary = null;

    /**
     * Apply full-width layout to parent form elements using inline styles.
     * Inline styles override any CSS specificity issues with Joget's stylesheets.
     *
     * Joget's CSS uses selectors like:
     *   .form-cell > label.label { width: 30%; float: left; }
     *   .form-container [class="form-cell-value"] { width: 70%; max-width: 70%; float: left; }
     *
     * We use inline styles to guarantee our layout takes precedence.
     *
     * NOTE: The FTL template may create its own .form-cell wrapper, AND Joget may add another
     * wrapper at a higher level. We traverse UP the entire ancestor chain to style ALL matching elements.
     */
    function applyFullWidthLayout(container) {
        console.log('[JRE] Applying full-width layout...');

        var fullWidthStyle = 'display: block !important; width: 100% !important; max-width: 100% !important; float: none !important;';
        var labelStyle = fullWidthStyle + ' margin-bottom: 8px !important; text-align: left !important;';
        var valueStyle = fullWidthStyle + ' margin-left: 0 !important; padding-left: 0 !important;';
        var cellStyle = fullWidthStyle + ' clear: both !important;';

        // Walk UP the DOM tree and style ALL .form-cell and .form-cell-value ancestors
        var element = container;
        var styledCount = 0;

        while (element && element !== document.body) {
            element = element.parentElement;
            if (!element) break;

            // Style any .form-cell or .form-cells ancestor
            if (element.classList.contains('form-cell') || element.classList.contains('form-cells')) {
                element.style.cssText += '; ' + cellStyle;
                element.classList.add('jre-form-cell-fullwidth');
                styledCount++;
                console.log('[JRE] Styled form-cell ancestor #' + styledCount);

                // Style direct child label (don't use :scope for compatibility)
                var children = element.children;
                for (var i = 0; i < children.length; i++) {
                    var child = children[i];
                    if (child.tagName === 'LABEL' && (child.classList.contains('label') || child.className === 'label')) {
                        child.style.cssText += '; ' + labelStyle;
                        console.log('[JRE] Styled label in form-cell #' + styledCount);
                    }
                }
            }

            // Style any .form-cell-value ancestor
            if (element.classList.contains('form-cell-value')) {
                element.style.cssText += '; ' + valueStyle;
                element.classList.add('jre-form-cell-value-fullwidth');
                console.log('[JRE] Styled form-cell-value ancestor');
            }

            // Style form-column
            if (element.classList.contains('form-column')) {
                element.style.cssText += '; ' + fullWidthStyle;
            }

            // Style form-section
            if (element.classList.contains('form-section')) {
                element.style.cssText += '; width: 100% !important;';
            }

            // Handle table cells (Joget sometimes uses table layouts)
            if (element.tagName === 'TD') {
                element.style.cssText += '; display: block !important; width: 100% !important;';
            }
            if (element.tagName === 'TR') {
                element.style.cssText += '; display: block !important; width: 100% !important;';
            }

            // Stop at form container to avoid affecting other form elements
            if (element.classList.contains('form-container') ||
                element.classList.contains('subform-container') ||
                element.id && element.id.indexOf('form-canvas') >= 0) {
                console.log('[JRE] Reached form container, stopping traversal');
                break;
            }
        }

        if (styledCount === 0) {
            console.warn('[JRE] No .form-cell ancestors found');
        } else {
            console.log('[JRE] Full-width layout applied to ' + styledCount + ' form-cell ancestor(s)');
        }
    }

    /**
     * Initialize the JRE Editor
     */
    function init(containerId, options) {
        var opts = Object.assign({}, defaults, options);
        var container = document.getElementById(containerId);

        if (!container) {
            console.error('JRE Editor: Container not found:', containerId);
            return null;
        }

        // Make parent form-cell and its containers full width (for Joget form layout)
        // Use INLINE STYLES to guarantee override of Joget's CSS (class-based overrides often fail due to specificity)
        applyFullWidthLayout(container);

        // Check API credentials
        if (!opts.apiId || !opts.apiKey) {
            console.warn('[JRE] API credentials not configured. Validation will not work.');
        }

        // Build the editor UI
        container.innerHTML = buildHTML(opts);

        // Get DOM elements
        var elements = {
            messages: container.querySelector('.jre-messages'),
            validateBtn: container.querySelector('.jre-validate-btn'),
            saveBtn: container.querySelector('.jre-save-btn'),
            clearBtn: container.querySelector('.jre-clear-btn'),
            sampleBtn: container.querySelector('.jre-sample-btn'),
            dictBtn: container.querySelector('.jre-dict-btn'),
            helpBtn: container.querySelector('.jre-help-btn'),
            dictPanel: container.querySelector('.jre-dict-panel'),
            dictContent: container.querySelector('.jre-dict-content'),
            dictSearch: container.querySelector('.jre-dict-search'),
            helpPanel: container.querySelector('.jre-help-panel'),
            status: container.querySelector('.jre-status'),
            textarea: container.querySelector('.jre-textarea'),
            hiddenField: document.getElementById(opts.hiddenFieldId),
            rulesetCodeField: opts.rulesetCodeFieldId ? document.getElementById(opts.rulesetCodeFieldId) : null
        };

        // Initialize CodeMirror
        var cmOptions = {
            mode: 'jre',
            lineNumbers: true,
            matchBrackets: true,
            styleActiveLine: true,
            indentUnit: 2,
            tabSize: 2,
            lineWrapping: true,
            gutters: ['CodeMirror-linenumbers', 'jre-gutter'],
            extraKeys: {
                'Ctrl-Enter': function() { validate(); },
                'Cmd-Enter': function() { validate(); },
                'Ctrl-S': function() { save(); },
                'Cmd-S': function() { save(); },
                'Ctrl-Space': function() { showAutocomplete(); },
                'Tab': function(cm) {
                    cm.replaceSelection('  ', 'end');
                }
            }
        };

        var cm = CodeMirror.fromTextArea(elements.textarea, cmOptions);

        // Get the main area and set CodeMirror to fill it
        var mainArea = container.querySelector('.jre-main-area');

        // Apply configured height to main area (from form properties)
        var editorHeight = opts.height;
        if (editorHeight && editorHeight !== 'auto') {
            // Parse height value (could be "400px" or "400")
            var heightVal = parseInt(editorHeight, 10);
            if (!isNaN(heightVal) && heightVal > 0) {
                mainArea.style.height = heightVal + 'px';
                console.log('[JRE] Set main area height to:', heightVal + 'px');
            }
        }

        // Tell CodeMirror to fill the available height
        // Use setTimeout to ensure layout is complete
        setTimeout(function() {
            var wrapperHeight = container.querySelector('.jre-editor-wrapper').offsetHeight;
            if (wrapperHeight > 0) {
                cm.setSize(null, wrapperHeight);
                console.log('[JRE] Set CodeMirror height to:', wrapperHeight + 'px');
            }
            cm.refresh();
        }, 50);

        var lineMarkers = [];
        var isDirty = false;

        // Load existing value
        if (elements.hiddenField && elements.hiddenField.value) {
            cm.setValue(elements.hiddenField.value);
        }

        // Load ruleset if code provided
        if (opts.rulesetCode) {
            loadRuleset(opts.rulesetCode);
        }

        updateStatus();

        // Sync to hidden field
        cm.on('change', function() {
            if (elements.hiddenField) {
                elements.hiddenField.value = cm.getValue();
            }
            isDirty = true;
            updateStatus();
        });

        // Button handlers
        elements.validateBtn.addEventListener('click', validate);

        if (elements.saveBtn) {
            elements.saveBtn.addEventListener('click', save);
        }

        elements.clearBtn.addEventListener('click', function() {
            if (confirm('Clear all content?')) {
                cm.setValue('');
                clearMarkers();
                showMessage('info', 'Editor Cleared', 'Enter rules and click Validate.');
            }
        });

        elements.sampleBtn.addEventListener('click', function() {
            var sample = getSampleScript();
            var current = cm.getValue();
            cm.setValue(current ? current + '\n\n' + sample : sample);
            clearMarkers();
            showMessage('info', 'Sample Inserted', 'Click Validate to check the rules.');
        });

        if (elements.dictBtn) {
            elements.dictBtn.addEventListener('click', toggleDictionary);
        }

        elements.helpBtn.addEventListener('click', function() {
            var isVisible = elements.helpPanel.classList.toggle('visible');
            this.textContent = isVisible ? '✕ Close' : '? Help';
        });

        // Dictionary search
        if (elements.dictSearch) {
            elements.dictSearch.addEventListener('input', filterDictionary);
        }

        // Load field dictionary
        if (opts.showDictionary) {
            loadFieldDictionary();
        }

        /**
         * Update status bar
         */
        function updateStatus() {
            var lines = cm.lineCount();
            var chars = cm.getValue().length;
            var dirtyMark = isDirty ? ' •' : '';
            elements.status.textContent = lines + ' lines, ' + chars + ' chars' + dirtyMark;
        }

        /**
         * Clear error markers
         */
        function clearMarkers() {
            lineMarkers.forEach(function(marker) {
                cm.removeLineClass(marker.line, 'background', marker.className);
            });
            lineMarkers = [];
            cm.clearGutter('jre-gutter');
        }

        /**
         * Add error marker to a line
         */
        function addMarker(lineNum, type) {
            var lineIndex = lineNum - 1;
            if (lineIndex < 0 || lineIndex >= cm.lineCount()) return;

            var className = type === 'error' ? 'jre-line-error' : 'jre-line-warning';
            cm.addLineClass(lineIndex, 'background', className);
            lineMarkers.push({ line: lineIndex, className: className });

            var marker = document.createElement('span');
            marker.className = type === 'error' ? 'jre-gutter-error' : 'jre-gutter-warning';
            marker.innerHTML = type === 'error' ? '●' : '▲';
            marker.title = type === 'error' ? 'Error' : 'Warning';
            cm.setGutterMarker(lineIndex, 'jre-gutter', marker);
        }

        /**
         * Show message in the messages panel
         */
        function showMessage(type, title, content, items) {
            elements.messages.className = 'jre-messages ' + type;

            var html = '<span class="jre-message-title">' + escapeHtml(title) + '</span>';
            if (content) {
                html += '<div>' + escapeHtml(content) + '</div>';
            }
            if (items && items.length > 0) {
                html += '<ul class="jre-error-list">';
                items.forEach(function(item) {
                    var itemClass = 'jre-error-item ' + (item.type || '');
                    html += '<li class="' + itemClass + '" data-line="' + (item.line || '') + '">';
                    html += '<span class="jre-error-icon">' + (item.icon || '•') + '</span>';
                    if (item.line) {
                        html += '<span class="jre-error-line-num">Line ' + item.line + '</span>';
                    }
                    html += '<span>' + escapeHtml(item.message) + '</span>';
                    html += '</li>';
                });
                html += '</ul>';
            }

            elements.messages.innerHTML = html;

            // Click to jump to line
            elements.messages.querySelectorAll('.jre-error-item[data-line]').forEach(function(el) {
                el.addEventListener('click', function() {
                    var line = parseInt(this.getAttribute('data-line'));
                    if (line > 0) {
                        cm.setCursor({ line: line - 1, ch: 0 });
                        cm.focus();
                    }
                });
            });
        }

        /**
         * Validate the script
         */
        function validate() {
            var script = cm.getValue();

            if (!opts.apiId || !opts.apiKey) {
                showMessage('error', 'Configuration Error', 'API credentials not configured. Please configure API ID and API Key in the form element properties.');
                return;
            }

            clearMarkers();
            showMessage('loading', 'Validating...', null);
            elements.validateBtn.disabled = true;

            apiCall('POST', '/validate', { script: script, scopeCode: opts.scopeCode }, function(response) {
                elements.validateBtn.disabled = false;
                displayValidationResult(response);
                if (opts.onValidate) {
                    opts.onValidate(response);
                }
            }, function(error) {
                elements.validateBtn.disabled = false;
                showMessage('error', 'Validation Failed', error);
                if (opts.onError) {
                    opts.onError(error);
                }
            });
        }

        /**
         * Save the ruleset
         */
        function save() {
            var script = cm.getValue();

            if (!script.trim()) {
                showMessage('error', 'Cannot Save', 'Script is empty');
                return;
            }

            if (!opts.apiId || !opts.apiKey) {
                showMessage('error', 'Configuration Error', 'API credentials not configured.');
                return;
            }

            showMessage('loading', 'Saving...', null);
            if (elements.saveBtn) elements.saveBtn.disabled = true;

            var data = {
                script: script,
                rulesetCode: opts.rulesetCode || '',
                rulesetName: 'Eligibility Rules',
                contextType: opts.contextType,
                contextCode: opts.contextCode,
                fieldScopeCode: opts.scopeCode
            };

            apiCall('POST', '/saveRuleset', data, function(response) {
                if (elements.saveBtn) elements.saveBtn.disabled = false;

                if (response.success) {
                    opts.rulesetCode = response.rulesetCode;
                    isDirty = false;
                    updateStatus();

                    if (elements.rulesetCodeField) {
                        elements.rulesetCodeField.value = response.rulesetCode;
                    }

                    showMessage('success', '✓ Saved',
                        'Ruleset ' + response.rulesetCode + ' (v' + response.version + ')');

                    if (opts.onSave) {
                        opts.onSave(response);
                    }
                } else {
                    displayValidationResult(response);
                }
            }, function(error) {
                if (elements.saveBtn) elements.saveBtn.disabled = false;
                showMessage('error', 'Save Failed', error);
            });
        }

        /**
         * Load a ruleset
         */
        function loadRuleset(code) {
            showMessage('loading', 'Loading...', null);

            apiCall('GET', '/loadRuleset?rulesetCode=' + encodeURIComponent(code), null, function(response) {
                if (response.success) {
                    cm.setValue(response.script || '');
                    opts.rulesetCode = response.rulesetCode;
                    isDirty = false;
                    updateStatus();

                    showMessage('info', 'Loaded',
                        response.rulesetName + ' (v' + response.version + ', ' + response.status + ')');
                } else {
                    showMessage('error', 'Load Failed', response.error || 'Unknown error');
                }
            }, function(error) {
                showMessage('error', 'Load Failed', error);
            });
        }

        /**
         * Display validation result
         */
        function displayValidationResult(response) {
            if (response.valid) {
                var items = [];
                if (response.rules) {
                    response.rules.forEach(function(rule) {
                        items.push({
                            icon: '✓',
                            message: rule.ruleName + ' (' + rule.ruleType + ')',
                            type: 'success'
                        });
                    });
                }
                showMessage('success', '✓ Valid',
                    response.ruleCount + ' rule(s) parsed', items);
            } else {
                var items = [];

                if (response.errors) {
                    response.errors.forEach(function(err) {
                        items.push({
                            icon: '✕',
                            line: err.line,
                            message: err.message,
                            type: 'error'
                        });
                        addMarker(err.line, 'error');
                    });
                }

                if (response.warnings) {
                    response.warnings.forEach(function(warn) {
                        items.push({
                            icon: '⚠',
                            line: warn.line,
                            message: warn.message,
                            type: 'warning'
                        });
                        addMarker(warn.line, 'warning');
                    });
                }

                showMessage('error', '✕ Invalid', null, items);
            }
        }

        /**
         * Toggle field dictionary panel
         */
        function toggleDictionary() {
            var isVisible = elements.dictPanel.classList.toggle('visible');
            elements.dictBtn.textContent = isVisible ? '✕ Fields' : '📖 Fields';

            // Refresh CodeMirror after toggle (editor width changed)
            setTimeout(function() {
                cm.refresh();
                console.log('[JRE] Dictionary toggled, CodeMirror refreshed');
            }, 10);
        }

        /**
         * Load field dictionary from API
         */
        function loadFieldDictionary() {
            if (!opts.apiId || !opts.apiKey) {
                console.warn('[JRE] Cannot load field dictionary - API credentials not configured');
                return;
            }

            apiCall('GET', '/fields?scopeCode=' + encodeURIComponent(opts.scopeCode), null, function(response) {
                fieldDictionary = response;
                renderDictionary(response);
            }, function(error) {
                console.error('Failed to load field dictionary:', error);
            });
        }

        /**
         * Render field dictionary
         */
        function renderDictionary(data) {
            if (!elements.dictContent) return;

            var html = '';

            if (data.categories) {
                data.categories.forEach(function(cat) {
                    html += '<div class="jre-dict-category">';
                    html += '<div class="jre-dict-category-name">' + escapeHtml(cat.category) + '</div>';

                    cat.fields.forEach(function(field) {
                        html += '<div class="jre-dict-field" data-field="' + escapeHtml(field.fieldId) + '">';
                        html += '<code>' + escapeHtml(field.fieldId) + '</code>';
                        html += '<span class="jre-dict-field-label">' + escapeHtml(field.fieldLabel) + '</span>';
                        html += '<span class="jre-dict-field-type">' + escapeHtml(field.fieldType) + '</span>';
                        if (field.isGrid) {
                            html += '<span class="jre-dict-field-grid">GRID</span>';
                        }
                        html += '</div>';
                    });

                    html += '</div>';
                });
            }

            elements.dictContent.innerHTML = html;

            // Click to insert field
            elements.dictContent.querySelectorAll('.jre-dict-field').forEach(function(el) {
                el.addEventListener('click', function() {
                    var fieldId = this.getAttribute('data-field');
                    cm.replaceSelection(fieldId);
                    cm.focus();
                });
            });
        }

        /**
         * Filter dictionary by search
         */
        function filterDictionary() {
            var query = elements.dictSearch.value.toLowerCase();
            var fields = elements.dictContent.querySelectorAll('.jre-dict-field');

            fields.forEach(function(el) {
                var fieldId = el.getAttribute('data-field').toLowerCase();
                var label = el.querySelector('.jre-dict-field-label').textContent.toLowerCase();
                var match = fieldId.includes(query) || label.includes(query);
                el.style.display = match ? '' : 'none';
            });

            // Show/hide empty categories
            elements.dictContent.querySelectorAll('.jre-dict-category').forEach(function(cat) {
                var hasVisible = cat.querySelectorAll('.jre-dict-field[style=""], .jre-dict-field:not([style])').length > 0;
                cat.style.display = hasVisible ? '' : 'none';
            });
        }

        /**
         * Show autocomplete
         */
        function showAutocomplete() {
            if (fieldDictionary && fieldDictionary.categories) {
                toggleDictionary();
            }
        }

        /**
         * API call helper - with Joget API Builder authentication
         *
         * Joget API responses are wrapped: {"code": "200", "message": "{...actual data...}", "date": "..."}
         */
        function apiCall(method, endpoint, data, onSuccess, onError) {
            var url = opts.apiBase + endpoint;

            console.log('[JRE API] ' + method + ' ' + url);

            var xhr = new XMLHttpRequest();
            xhr.open(method, url, true);
            xhr.setRequestHeader('Content-Type', 'application/json');

            // Add API authentication headers
            if (opts.apiId) {
                xhr.setRequestHeader('api_id', opts.apiId);
            }
            if (opts.apiKey) {
                xhr.setRequestHeader('api_key', opts.apiKey);
            }

            xhr.onreadystatechange = function() {
                if (xhr.readyState === 4) {
                    console.log('[JRE API] Response:', xhr.status, xhr.responseText.substring(0, 300));

                    try {
                        var rawResponse = JSON.parse(xhr.responseText);
                        var parsed = parseJogetApiResponse(rawResponse);

                        if (parsed.success) {
                            onSuccess(parsed.data);
                        } else {
                            onError(parsed.error);
                        }
                    } catch (e) {
                        console.error('[JRE API] Parse error:', e);
                        onError('Invalid JSON response: ' + e.message);
                    }
                }
            };

            xhr.onerror = function() {
                console.error('[JRE API] Network error');
                onError('Network error');
            };

            xhr.send(data ? JSON.stringify(data) : null);
        }

        /**
         * Parse Joget API response format
         * Joget wraps responses: {"code": "200", "message": "{...}", "date": "..."}
         */
        function parseJogetApiResponse(response) {
            // Check if this is a Joget wrapped response
            if (response && response.code !== undefined) {
                var code = parseInt(response.code);

                if (code >= 200 && code < 300) {
                    // Success - parse the message field
                    if (response.message) {
                        try {
                            var data = typeof response.message === 'string'
                                ? JSON.parse(response.message)
                                : response.message;
                            return { success: true, data: data };
                        } catch (e) {
                            // Message might be plain text
                            return { success: true, data: { message: response.message } };
                        }
                    }
                    return { success: true, data: {} };
                } else {
                    // Error
                    var errorMsg = response.message || 'Request failed: ' + code;
                    if (response.error && response.error.message) {
                        errorMsg = response.error.message;
                    }
                    return { success: false, error: errorMsg };
                }
            }

            // Not wrapped - return as-is
            return { success: true, data: response };
        }

        /**
         * Escape HTML
         */
        function escapeHtml(str) {
            if (!str) return '';
            var div = document.createElement('div');
            div.textContent = str;
            return div.innerHTML;
        }

        // Return public API
        return {
            getCodeMirror: function() { return cm; },
            getValue: function() { return cm.getValue(); },
            setValue: function(val) { cm.setValue(val); isDirty = false; updateStatus(); },
            validate: validate,
            save: save,
            loadRuleset: loadRuleset,
            clearMarkers: clearMarkers,
            isDirty: function() { return isDirty; }
        };
    }

    /**
     * Build the editor HTML
     */
    function buildHTML(opts) {
        var saveBtn = opts.showSaveButton ? '<button type="button" class="jre-save-btn">💾 Save</button>' : '';
        var dictBtn = opts.showDictionary ? '<button type="button" class="jre-dict-btn">📖 Fields</button>' : '';

        var dictPanel = opts.showDictionary ? '\
<div class="jre-dict-panel">\
    <div class="jre-dict-header">\
        <input type="text" class="jre-dict-search" placeholder="Search fields...">\
    </div>\
    <div class="jre-dict-content"></div>\
</div>' : '';

        return '\
<div class="jre-messages">\
    <span class="jre-message-title">Rule Editor</span>\
    <div>Enter rules. Press <kbd>Ctrl+Enter</kbd> to validate, <kbd>Ctrl+S</kbd> to save.</div>\
</div>\
<div class="jre-toolbar">\
    <button type="button" class="jre-validate-btn jre-btn-primary">✓ Validate</button>\
    ' + saveBtn + '\
    <button type="button" class="jre-clear-btn">Clear</button>\
    <button type="button" class="jre-sample-btn">Sample</button>\
    ' + dictBtn + '\
    <button type="button" class="jre-help-btn">? Help</button>\
    <div class="jre-toolbar-spacer"></div>\
    <span class="jre-status"></span>\
</div>\
<div class="jre-main-area">\
    <div class="jre-editor-wrapper">\
        <textarea class="jre-textarea"></textarea>\
    </div>\
    ' + dictPanel + '\
</div>\
<div class="jre-help-panel">\
    <h4>Rules Script Quick Reference</h4>\
    <div class="jre-help-columns">\
        <div class="jre-help-column">\
            <strong>Structure:</strong>\
            <ul>\
                <li><code>RULE "name"</code> - Start a rule</li>\
                <li><code>TYPE:</code> INCLUSION | EXCLUSION | PRIORITY | BONUS</li>\
                <li><code>MANDATORY:</code> YES | NO</li>\
                <li><code>WHEN</code> condition</li>\
                <li><code>FAIL MESSAGE:</code> "text"</li>\
            </ul>\
        </div>\
        <div class="jre-help-column">\
            <strong>Operators:</strong>\
            <ul>\
                <li><code>=  !=  &gt;  &gt;=  &lt;  &lt;=</code></li>\
                <li><code>BETWEEN x AND y</code></li>\
                <li><code>IN ("a", "b", "c")</code></li>\
                <li><code>IS EMPTY  IS NOT EMPTY</code></li>\
            </ul>\
        </div>\
        <div class="jre-help-column">\
            <strong>Logic & Functions:</strong>\
            <ul>\
                <li><code>AND  OR  NOT  ( )</code></li>\
                <li><code>COUNT(grid) &gt; 0</code></li>\
                <li><code>HAS_ANY(grid.field, "val")</code></li>\
                <li><code>#</code> Comment line</li>\
            </ul>\
        </div>\
    </div>\
</div>';
    }

    /**
     * Get sample Rules Script
     */
    function getSampleScript() {
        return '\
# Sample Rules Script\n\
\n\
RULE "Adult Farmer"\n\
  TYPE: INCLUSION\n\
  MANDATORY: YES\n\
  ORDER: 10\n\
  WHEN age >= 18\n\
  FAIL MESSAGE: "Must be 18 years or older"\n\
\n\
RULE "Has Agricultural Activity"\n\
  TYPE: INCLUSION\n\
  MANDATORY: YES\n\
  ORDER: 20\n\
  WHEN hasCrops = true OR hasLivestock = true\n\
  FAIL MESSAGE: "Must be engaged in farming"\n\
\n\
RULE "Female Headed Household Bonus"\n\
  TYPE: BONUS\n\
  SCORE: 10\n\
  WHEN femaleHeadedHousehold = true\n\
  PASS MESSAGE: "Priority given to female-headed households"';
    }

    return {
        init: init
    };

})();
