import { Component, input } from '@angular/core';

/** Logo originale del team Depeche Code: parentesi di codice con un equalizzatore al centro. */
@Component({
  selector: 'dc-logo',
  template: `
    <svg [attr.width]="size()" [attr.height]="size()" viewBox="0 0 64 64" role="img" aria-label="Depeche Code">
      <rect x="2" y="2" width="60" height="60" rx="16" fill="#ffffff" />
      <path d="M17 20 L8 32 L17 44" fill="none" stroke="#2175D9" stroke-width="5" stroke-linecap="round" stroke-linejoin="round" />
      <path d="M47 20 L56 32 L47 44" fill="none" stroke="#2175D9" stroke-width="5" stroke-linecap="round" stroke-linejoin="round" />
      <rect class="bar b1" x="21" y="28" width="4.5" height="8" rx="2.2" fill="#103A6C" />
      <rect class="bar b2" x="27.5" y="22" width="4.5" height="20" rx="2.2" fill="#2175D9" />
      <rect class="bar b3" x="34" y="25" width="4.5" height="14" rx="2.2" fill="#103A6C" />
      <rect class="bar b4" x="40.5" y="29" width="4.5" height="6" rx="2.2" fill="#2175D9" />
    </svg>
  `,
  styles: `
    :host { display: inline-flex; }
    .bar { transform-box: fill-box; transform-origin: center; animation: eq 1.6s ease-in-out infinite; }
    .b2 { animation-delay: .2s; } .b3 { animation-delay: .4s; } .b4 { animation-delay: .6s; }
    @keyframes eq { 0%, 100% { transform: scaleY(1); } 50% { transform: scaleY(.6); } }
    @media (prefers-reduced-motion: reduce) { .bar { animation: none; } }
  `,
})
export class BrandLogo {
  readonly size = input(44);
}
