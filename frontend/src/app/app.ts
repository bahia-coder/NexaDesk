import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { ToastContainerComponent } from './shared/ui/toast-container.component';

@Component({
  selector: 'nx-root',
  imports: [RouterOutlet, ToastContainerComponent],
  template: '<router-outlet /><nx-toast-container />',
})
export class App {}
