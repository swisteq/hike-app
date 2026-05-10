/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ["./src/**/*.{js,jsx,ts,tsx}"],
  theme: {
    extend: {
      colors: {
        mountain: {
          50:  '#f0f7ff',
          100: '#e0effe',
          200: '#baddfd',
          300: '#7dc3fc',
          400: '#38a3f8',
          500: '#0e87e9',
          600: '#0269c7',
          700: '#0354a1',
          800: '#074785',
          900: '#0c3d6e',
        }
      }
    }
  },
  plugins: []
}
