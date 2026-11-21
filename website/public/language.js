// Language handling shared by the website (VitePress) and the Javadoc of libcobj.
//
// The site has the same pages in two languages: English at /... and Japanese
// at /ja/... (for example /guide/usage and /ja/guide/usage, or
// /javadoc/libcobj/index.html and /ja/javadoc/libcobj/index.html).
//
// - A visitor who comes from outside the site to the English top page (/ or
//   the top of the Javadoc) is sent to the Japanese one if they chose
//   Japanese before, or, if they have not chosen yet, if their browser
//   prefers Japanese. Other pages are never redirected: a page opened from a
//   search result or a shared link is shown in its own language, and search
//   engines can index both languages.
// - The language the visitor switches to (with the language menu of the
//   website or the link at the top of the Javadoc) is remembered.
// - Links with the class "language-switch" are pointed at the same page in the
//   other language.
(function () {
  'use strict';

  var STORAGE_KEY = 'hagane-cobol-language';

  if (location.protocol !== 'http:' && location.protocol !== 'https:') {
    return;
  }

  function languageOf(path) {
    return path === '/ja' || path.indexOf('/ja/') === 0 ? 'ja' : 'en';
  }

  function pathIn(language, path) {
    var english = languageOf(path) === 'ja' ? path.slice(3) || '/' : path;
    return language === 'ja' ? '/ja' + english : english;
  }

  function otherLanguageUrl() {
    var other = languageOf(location.pathname) === 'ja' ? 'en' : 'ja';
    return pathIn(other, location.pathname) + location.search + location.hash;
  }

  function load() {
    try {
      var value = localStorage.getItem(STORAGE_KEY);
      return value === 'ja' || value === 'en' ? value : null;
    } catch (e) {
      return null;
    }
  }

  function save(language) {
    try {
      localStorage.setItem(STORAGE_KEY, language);
    } catch (e) {
      // Storage may be disabled. The choice is then not remembered.
    }
  }

  function detect() {
    var languages = navigator.languages && navigator.languages.length
      ? navigator.languages
      : [navigator.language || ''];
    for (var i = 0; i < languages.length; i++) {
      var primary = String(languages[i]).toLowerCase().split('-')[0];
      if (primary === 'ja' || primary === 'en') {
        return primary;
      }
    }
    return 'en';
  }

  function isEnglishTopPage(path) {
    return path === '/' || path === '/index.html'
      || path === '/javadoc/libcobj/' || path === '/javadoc/libcobj/index.html';
  }

  function cameFromThisSite() {
    try {
      return document.referrer !== '' && new URL(document.referrer).origin === location.origin;
    } catch (e) {
      return false;
    }
  }

  // Moving within the site (including the language switch itself) never
  // redirects, so the switch works even when the choice cannot be saved.
  if (isEnglishTopPage(location.pathname) && !cameFromThisSite()) {
    var preferred = load() || detect();
    if (preferred === 'ja') {
      location.replace(pathIn('ja', location.pathname) + location.search + location.hash);
      return;
    }
  }

  document.addEventListener('click', function (event) {
    var target = event.target;
    var link = target && target.closest ? target.closest('a[href]') : null;
    if (!link || link.origin !== location.origin) {
      return;
    }
    // The URL may have changed without loading a page (the Javadoc search page
    // rewrites the query), so point the switch at the current page now.
    if (link.classList.contains('language-switch')) {
      link.href = otherLanguageUrl();
    }
    // Opening the link in another tab is not a choice of language.
    if (event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) {
      return;
    }
    var language = languageOf(link.pathname);
    if (language !== languageOf(location.pathname)) {
      save(language);
    }
  }, true);

  function updateSwitchLinks() {
    var links = document.querySelectorAll('a.language-switch');
    for (var i = 0; i < links.length; i++) {
      links[i].href = otherLanguageUrl();
    }
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', updateSwitchLinks);
  } else {
    updateSwitchLinks();
  }
  // The Javadoc changes the hash when moving within a page.
  window.addEventListener('hashchange', updateSwitchLinks);
})();
