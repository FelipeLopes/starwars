﻿CREATE TABLE Planets (
	Id SERIAL,
	PlanetName VARCHAR(255) NOT NULL,
	Climate VARCHAR(255) NOT NULL,
	Terrain VARCHAR(255) NOT NULL,
	NumFilms INT NOT NULL,
	PRIMARY KEY (Id)
);
	