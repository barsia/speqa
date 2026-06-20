// ========== SPEQA LANDING PAGE — JavaScript ==========
(function() {
    'use strict';

    // ---- Theme Toggle (Light/Dark) ----
    var themeBtn = document.getElementById('themeToggle');
    var savedTheme = localStorage.getItem('speqa-theme') || (window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');
    document.documentElement.setAttribute('data-theme', savedTheme);

    if (themeBtn) {
        themeBtn.addEventListener('click', function() {
            var current = document.documentElement.getAttribute('data-theme');
            var next = current === 'dark' ? 'light' : 'dark';
            document.documentElement.setAttribute('data-theme', next);
            localStorage.setItem('speqa-theme', next);
        });
    }

    // ---- Nav scroll effect ----
    var navEl = document.getElementById('nav');
    if (window.scrollY > 16) {
        navEl.classList.add('scrolled');
    } else {
        window.addEventListener('scroll', function() {
            if (window.scrollY > 16) {
                navEl.classList.add('scrolled');
            } else {
                navEl.classList.remove('scrolled');
            }
        }, { passive: true });
    }

    // ---- Smooth scroll for anchor links ----
    document.querySelectorAll('a[href^="#"]').forEach(function(anchor) {
        anchor.addEventListener('click', function(e) {
            var href = this.getAttribute('href');
            if (href === '#') return;
            var target = document.querySelector(href);
            if (target) {
                e.preventDefault();
                target.scrollIntoView({ behavior:'smooth', block:'start' });
            }
        });
    });

})();
