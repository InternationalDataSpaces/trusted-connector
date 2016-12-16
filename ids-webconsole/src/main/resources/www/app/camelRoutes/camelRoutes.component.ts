import { Component, OnInit, EventEmitter, Output } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { FormsModule }   from '@angular/forms';
import { Title } from '@angular/platform-browser'

import { Route } from './route';
import { CamelRoutesService } from './camelRoutes.service';

@Component({
  selector: 'camelRoutes',
  templateUrl: 'app/camelRoutes/camelRoutes.component.html',
})

export class CamelRoutesComponent  implements OnInit{

  title = 'Current Routes';
  routes: Route[];
  selectedRoute: Route;

  @Output() changeTitle = new EventEmitter();

  constructor(private titleService: Title, private camelRoutesService: CamelRoutesService) {
    this.titleService.setTitle("Data Pipes");

    this.camelRoutesService.getRoutes().subscribe(routes => {
       this.routes = routes;
     });
  }

  ngOnInit(): void {
    this.changeTitle.emit('Camel Routes');
  }

  onSelect(route: Route): void {
      this.selectedRoute = route;
  }
}
