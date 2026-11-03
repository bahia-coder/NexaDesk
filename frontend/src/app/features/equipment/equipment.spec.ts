import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { computed, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { Role } from '../../core/models/user.models';
import { EquipmentDetailPage } from './equipment-detail.page';
import { Equipment, equipmentActions, equipmentDatesError } from './equipment.models';

const LAPTOP: Equipment = {
  id: 4,
  inventoryCode: 'EQ-0004',
  name: 'Portable de Sara Idrissi',
  type: 'LAPTOP',
  brand: 'Dell',
  model: 'Latitude 5440',
  serialNumber: 'DL5440-0042',
  acquisitionDate: '2024-03-12',
  warrantyEndDate: '2027-03-12',
  underWarranty: true,
  status: 'IN_USE',
  location: null,
  assignedUser: { id: 9, fullName: 'Sara Idrissi', avatarColor: '#2563eb' },
  shared: false,
  notes: null,
  version: 0,
  createdAt: '2024-03-12T09:00:00Z',
  updatedAt: '2024-03-12T09:00:00Z',
};

describe('Equipment rules', () => {
  it('offers the API’s transitions for each state and role', () => {
    expect(equipmentActions(LAPTOP, 'ADMIN')).toEqual([
      'ASSIGN',
      'UNASSIGN',
      'SEND_TO_REPAIR',
      'EDIT',
      'RETIRE',
    ]);
    expect(equipmentActions(LAPTOP, 'TECHNICIAN')).toEqual(['SEND_TO_REPAIR']);
    expect(equipmentActions(LAPTOP, 'EMPLOYEE')).toEqual([]);

    // In repair, it keeps its holder and cannot be reassigned.
    expect(equipmentActions({ ...LAPTOP, status: 'IN_REPAIR' }, 'ADMIN')).toEqual([
      'BACK_IN_USE',
      'EDIT',
      'RETIRE',
    ]);
    // A shared printer: deployed at a location, nobody holds it.
    const printer: Equipment = {
      ...LAPTOP,
      type: 'PRINTER',
      status: 'IN_STOCK',
      assignedUser: null,
      location: '2e étage',
    };
    expect(equipmentActions(printer, 'TECHNICIAN')).toEqual(['SEND_TO_REPAIR', 'DEPLOY_SHARED']);
    expect(equipmentActions({ ...printer, location: null }, 'TECHNICIAN')).toEqual([
      'SEND_TO_REPAIR',
    ]);
    expect(equipmentActions({ ...printer, status: 'IN_USE', shared: true }, 'TECHNICIAN')).toEqual([
      'SEND_TO_REPAIR',
      'BACK_TO_STOCK',
    ]);
    expect(equipmentActions({ ...LAPTOP, status: 'RETIRED' }, 'ADMIN')).toEqual([]);
  });

  it('checks the dates like the API', () => {
    expect(equipmentDatesError('2026-11-03', '2029-11-03', '2026-11-03')).toBeNull();
    expect(equipmentDatesError('2026-11-04', null, '2026-11-03')).toContain('future');
    expect(equipmentDatesError('2026-01-10', '2025-12-31', '2026-11-03')).toContain(
      'fin de garantie',
    );
    expect(equipmentDatesError(null, '2020-01-01', '2026-11-03')).toBeNull();
  });
});

describe('Equipment page', () => {
  let http: HttpTestingController;
  const role = signal<Role>('ADMIN');

  beforeEach(() => {
    role.set('ADMIN');
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: { role: computed(() => role()) } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function open() {
    const fixture = TestBed.createComponent(EquipmentDetailPage);
    fixture.componentRef.setInput('id', '4');
    fixture.autoDetectChanges();
    http.expectOne('/api/equipment/4').flush(LAPTOP);
    if (role() !== 'EMPLOYEE') {
      http
        .expectOne('/api/equipment/4/assignments')
        .flush([
          {
            id: 1,
            user: LAPTOP.assignedUser,
            assignedAt: '2024-03-12T09:00:00Z',
            returnedAt: null,
          },
        ]);
      http.expectOne('/api/equipment/4/tickets').flush([]);
    }
    await fixture.whenStable();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  function buttons(el: HTMLElement): string[] {
    return Array.from(el.querySelectorAll('.actions .btn')).map((b) =>
      (b.textContent ?? '').trim(),
    );
  }

  it('sends a device to repair and shows its new state', async () => {
    const { fixture, el } = await open();
    expect(buttons(el)).toEqual([
      'Signaler un incident',
      'Réaffecter',
      'Remettre en stock',
      'Envoyer en réparation',
      'Modifier',
      'Mettre au rebut',
    ]);
    expect(el.querySelector('.history')?.textContent).toContain('depuis le');

    Array.from(el.querySelectorAll<HTMLButtonElement>('.actions button'))
      .find((b) => b.textContent?.includes('Envoyer en réparation'))!
      .click();
    const put = http.expectOne({ method: 'PUT', url: '/api/equipment/4/status' });
    expect(put.request.body).toEqual({ status: 'IN_REPAIR' });
    put.flush({ ...LAPTOP, status: 'IN_REPAIR' });
    await fixture.whenStable();
    http.expectOne('/api/equipment/4/assignments').flush([]);
    await fixture.whenStable();
    expect(el.querySelector('.title-row .badge')?.textContent).toContain('En réparation');
    expect(buttons(el)).toEqual([
      'Signaler un incident',
      'Retour de réparation',
      'Modifier',
      'Mettre au rebut',
    ]);
  });

  it('is read-only for the employee who holds it, with no staff histories', async () => {
    role.set('EMPLOYEE');
    const { el } = await open();
    expect(buttons(el)).toEqual(['Signaler un incident']);
    expect(el.querySelector('.history')).toBeNull();
    expect(el.textContent).not.toContain('Incidents');
  });
});
