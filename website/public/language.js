// Language handling shared by the website (VitePress) and the Javadoc of libcobj.
//
// The site has the same pages in two languages: English at /... and Japanese
// at /ja/... (for example /guide/usage and /ja/guide/usage, or
// /javadoc/libcobj/index.html and /ja/javadoc/libcobj/index.html).
//
// - On the first visit, the language is chosen from the browser settings and
//   the visitor is sent to the page in that language. The choice is saved, so
//   this happens only once.
// - When the visitor follows a link to the other language (the language menu
//   of the website or the link at the top of the Javadoc), the new language is
//   saved and used from then on.
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
      // Storage may be disabled. The language is then detected on every visit.
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

  var current = languageOf(location.pathname);
  var preferred = load();
  if (preferred === null) {
    preferred = detect();
    save(preferred);
    if (preferred !== current) {
      location.replace(pathIn(preferred, location.pathname) + location.search + location.hash);
      return;
    }
  }

  // Remember the language when the visitor follows a link to the other one.
  document.addEventListener('click', function (event) {
    var target = event.target;
    var link = target && target.closest ? target.closest('a[href]') : null;
    if (!link || link.origin !== location.origin) {
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
      var other = languageOf(location.pathname) === 'ja' ? 'en' : 'ja';
      links[i].href = pathIn(other, location.pathname) + location.search + location.hash;
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
