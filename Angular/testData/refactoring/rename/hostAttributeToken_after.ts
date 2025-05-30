// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import {Attribute, ChangeDetectionStrategy, Component, HostAttributeToken, inject} from '@angular/core';

@Component({
  selector: 'app-sub-component',
  standalone: true,
  template: `<div [attr.data-testId]="dataTestIdContexts">foo</div> {{foo}} - {{dataTestIdContexts}}`,
})
export class SubComponent {
  dataTestIdContexts = inject(new HostAttributeToken('test<caret>'));
}

@Component({
  selector: 'app-root',
  standalone: true,
  template: `
    <app-sub-component test="my-test-id"/>
  `,
  imports: [SubComponent],
})
export class AppComponent {}

