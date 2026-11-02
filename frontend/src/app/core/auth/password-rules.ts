import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

export interface PasswordRule {
  label: string;
  ok: boolean;
}

/**
 * Same rule as the API (backend: StrongPasswordValidator): 10 to 128 characters with an uppercase letter,
 * a lowercase letter, a digit and a special character. Checked here for instant feedback only; the server
 * remains the authority.
 */
export function passwordRules(value: string): PasswordRule[] {
  const chars = [...value];
  return [
    { label: '10 caractères minimum', ok: value.length >= 10 && value.length <= 128 },
    { label: 'Une majuscule', ok: chars.some((c) => c !== c.toLowerCase()) },
    { label: 'Une minuscule', ok: chars.some((c) => c !== c.toUpperCase()) },
    { label: 'Un chiffre', ok: /\d/.test(value) },
    {
      label: 'Un caractère spécial',
      ok: chars.some((c) => c.toLowerCase() === c.toUpperCase() && !/[\d\s]/.test(c)),
    },
  ];
}

export const strongPassword: ValidatorFn = (control: AbstractControl): ValidationErrors | null =>
  passwordRules(String(control.value ?? '')).every((rule) => rule.ok)
    ? null
    : { weakPassword: true };
