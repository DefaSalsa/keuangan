/** Tailwind dipakai saat build (lihat workflow): kelas dipindai dari index.html dan app.js. */
module.exports = {
  content: ['../app/src/main/assets/www/**/*.{html,js}'],
  theme: {
    extend: {
      colors: {
        ink: '#000000',
        surface: '#0D0D12',
        sheet: '#0A0A0F',
        line: '#1B2744',
        brand: '#2979FF',
        brandlt: '#5C9DFF',
        branddim: '#1F4FB8',
        mute: '#8A94A8',
        note: '#B8C2D6',
        neg: '#FF5252',
        pos: '#2ECC71',
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', '-apple-system', 'Roboto', 'Segoe UI', 'sans-serif'],
      },
      keyframes: {
        fadeUp: { '0%': { opacity: '0', transform: 'translateY(16px)' }, '100%': { opacity: '1', transform: 'none' } },
        fadeIn: { '0%': { opacity: '0' }, '100%': { opacity: '1' } },
        fadeOut: { '0%': { opacity: '1' }, '100%': { opacity: '0' } },
        sheetIn: {
          '0%': { transform: 'translateY(100%)' },
          '70%': { transform: 'translateY(-1.5%)' },
          '100%': { transform: 'translateY(0)' },
        },
        sheetOut: { '0%': { transform: 'translateY(0)' }, '100%': { transform: 'translateY(100%)' } },
        pop: {
          '0%': { opacity: '0', transform: 'scale(.6)' },
          '70%': { opacity: '1', transform: 'scale(1.06)' },
          '100%': { transform: 'scale(1)' },
        },
        shake: {
          '0%,100%': { transform: 'translateX(0)' },
          '20%': { transform: 'translateX(-8px)' },
          '40%': { transform: 'translateX(7px)' },
          '60%': { transform: 'translateX(-5px)' },
          '80%': { transform: 'translateX(3px)' },
        },
        float: {
          '0%,100%': { transform: 'translate3d(0,0,0) scale(1)' },
          '50%': { transform: 'translate3d(-18px,22px,0) scale(1.08)' },
        },
        ring: {
          '0%': { boxShadow: '0 0 0 0 rgba(41,121,255,.55)' },
          '100%': { boxShadow: '0 0 0 18px rgba(41,121,255,0)' },
        },
        toastIn: {
          '0%': { opacity: '0', transform: 'translate(-50%,-14px) scale(.96)' },
          '100%': { opacity: '1', transform: 'translate(-50%,0) scale(1)' },
        },
      },
      animation: {
        'fade-up': 'fadeUp .5s cubic-bezier(.2,.8,.2,1) backwards',
        'fade-in': 'fadeIn .25s ease-out backwards',
        'fade-out': 'fadeOut .2s ease-in forwards',
        'sheet-in': 'sheetIn .42s cubic-bezier(.2,.9,.25,1) backwards',
        'sheet-out': 'sheetOut .22s ease-in forwards',
        pop: 'pop .45s cubic-bezier(.2,.9,.25,1.2) backwards',
        shake: 'shake .4s ease-in-out',
        float: 'float 9s ease-in-out infinite',
        ring: 'ring 2.6s ease-out infinite',
        'toast-in': 'toastIn .35s cubic-bezier(.2,.9,.25,1.1) backwards',
      },
    },
  },
  plugins: [],
};
