-- Työmaakokousraportti

INSERT INTO raportti (nimi, kuvaus, konteksti, parametrit, koodi)
VALUES ('tyomaakokous', 'Työmaakokousraportti',
        ARRAY['urakka'::raporttikonteksti],
	ARRAY[('Hoitokausi', 'hoitokausi', true, 'urakka'::raporttikonteksti)::raporttiparametri,
	      ('Kuukausi', 'hoitokauden-kuukausi', true, 'urakka'::raporttikonteksti)::raporttiparametri,
	      ('Laskutusyhteenveto', 'checkbox', true, 'urakka'::raporttikonteksti)::raporttiparametri,
	      ('Yksikköhintaisten töiden raportti', 'checkbox', true, 'urakka'::raporttikonteksti)::raporttiparametri,
	      ('Ympäristöraportti', 'checkbox', true, 'urakka'::raporttikonteksti)::raporttiparametri
	      -- lisää raportteja: lisätyöt ja äkilliset hoitotyöt, muistutukset ja sanktiot, turvallisuusraportti
	      ],
        '#''harja.palvelin.raportointi.raportit.tyomaakokous/suorita');
