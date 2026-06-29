package com.chris.financeapp.utils

object PensionScraper {

    // Javascript script to auto-detect potential currency amounts in the page
    val AUTO_DETECT_JS = """
        (function() {
            var regex = /£\s*([0-9,]+(\.[0-9]{2})?)/g;
            var bodyText = document.body.innerText;
            var matches = [];
            var match;
            while ((match = regex.exec(bodyText)) !== null) {
                var val = parseFloat(match[1].replace(/,/g, ''));
                if (!isNaN(val) && val > 0) {
                    matches.push(val);
                }
            }
            // Sort descending and return the largest amount as a candidate (pension pots are usually the largest numbers on screen)
            if (matches.length > 0) {
                matches.sort(function(a, b){ return b - a; });
                return matches[0];
            }
            return null;
        })();
    """.trimIndent()

    // Javascript script to enable click-to-scrape mode
    // It highlights elements under the cursor and returns the text of the clicked element
    val ENABLE_CLICK_SCRAPE_JS = """
        (function() {
            if (window.clickScrapeEnabled) return;
            window.clickScrapeEnabled = true;

            var prevElement = null;

            document.addEventListener('mouseover', function(e) {
                var el = e.target;
                if (prevElement) {
                    prevElement.style.outline = prevElement.originalOutline || '';
                }
                el.originalOutline = el.style.outline;
                el.style.outline = '2px solid #6366F1'; // Accent outline color
                prevElement = el;
            });

            document.addEventListener('click', function(e) {
                e.preventDefault();
                e.stopPropagation();
                var text = e.target.innerText || e.target.textContent;
                // Try to parse money from the clicked element
                var match = text.match(/£?\s*([0-9,]+(\.[0-9]{2})?)/);
                if (match) {
                    var val = parseFloat(match[1].replace(/,/g, ''));
                    if (!isNaN(val)) {
                        AndroidScraperInterface.onValueSelected(val, text);
                    }
                } else {
                    AndroidScraperInterface.onError("No numeric amount found in clicked element: " + text);
                }
            }, true);
        })();
    """.trimIndent()
}
