import { vi } from 'vitest';
import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';
import { SignatureDialogComponent } from './signature-dialog.component';
import { WorkInstructionApiService } from '../../services/work-instruction-api.service';
import { WorkInstructionDto } from '../../models/work-instruction.model';

const APPROVED_DTO = { id: 'wi-1', revisionStatus: 'APPROVED' } as WorkInstructionDto;

describe('SignatureDialogComponent', () => {
  let fixture: ComponentFixture<SignatureDialogComponent>;
  let component: SignatureDialogComponent;

  const mockApi = {
    approve: vi.fn().mockReturnValue(of(APPROVED_DTO)),
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [SignatureDialogComponent],
      providers: [
        provideNoopAnimations(),
        { provide: WorkInstructionApiService, useValue: mockApi },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(SignatureDialogComponent);
    component = fixture.componentInstance;
    component.workInstructionId = 'wi-1';
    fixture.detectChanges();
  });

  it('does not call approve when password is empty', () => {
    component.password = '';
    component.confirm();
    expect(mockApi.approve).not.toHaveBeenCalled();
  });

  it('emits approved and closes on success', () => {
    const approvedSpy = vi.fn();
    const visibleSpy = vi.fn();
    component.approved.subscribe(approvedSpy);
    component.visibleChange.subscribe(visibleSpy);
    component.password = 'secret';

    component.confirm();

    expect(mockApi.approve).toHaveBeenCalledWith('wi-1', 'secret');
    expect(approvedSpy).toHaveBeenCalledWith(APPROVED_DTO);
    expect(visibleSpy).toHaveBeenCalledWith(false);
    expect(component.password).toBe('');
    expect(component.error).toBe('');
  });

  it('displays a verification error on 422 and does not emit approved', () => {
    const approvedSpy = vi.fn();
    component.approved.subscribe(approvedSpy);
    mockApi.approve.mockReturnValueOnce(throwError(() => ({ status: 422, error: {} })));
    component.password = 'wrong';

    component.confirm();

    expect(component.error).toBe('Password verification failed');
    expect(approvedSpy).not.toHaveBeenCalled();
    expect(component.submitting).toBe(false);
  });

  it('surfaces a server-provided error message', () => {
    mockApi.approve.mockReturnValueOnce(
      throwError(() => ({ status: 422, error: { error: 'Revision not pending' } })));
    component.password = 'secret';

    component.confirm();

    expect(component.error).toBe('Revision not pending');
  });
});
