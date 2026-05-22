(ns harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu-nakyma
	(:require [reagent.core :as r]
					[harja.ui.primitiivit.viesti :as viesti]
					[harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.modaalit :as modaalit]
					[harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.panelit :as panelit]
					[harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.renderointi :as renderointi]
					[harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.valilehdet :as valilehdet]
					[harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.viestit :as viestit]))

(def ^:private yhteiset-teematokenit
	[{:avain :harja-teema-pinta
	  :nimi "Pintavari"
	  :tyyppi :color
	  :oletus "#ffffff"}
	 {:avain :harja-teema-pinta-hover
	  :nimi "Pinnan hover-vari"
	  :tyyppi :color
	  :oletus "#e6f2ff"}
	 {:avain :harja-teema-reuna
	  :nimi "Reunan vari"
	  :tyyppi :color
	  :oletus "#afafaf"}
	 {:avain :harja-teema-teksti
	  :nimi "Tekstin vari"
	  :tyyppi :color
	  :oletus "#1f1f1f"}
	 {:avain :harja-teema-korostus
	  :nimi "Korostusvari"
	  :tyyppi :color
	  :oletus "#0066cc"}
	 {:avain :harja-teema-korostus-hover
	  :nimi "Korostuksen hover-vari"
	  :tyyppi :color
	  :oletus "#0088cc"}
	 {:avain :harja-teema-radius
	  :nimi "Kulman sade"
	  :tyyppi :text
	  :esimerkki "0.25rem"
	  :oletus "0.25rem"}])

(defn ui-komponenttien-tarkastelu []
	(let [modaalin-tila (r/atom {:nakyvissa? false})
			valilehtien-tilat (r/atom {})
			tyylioverridet-tilat (r/atom {})
			tyylioverride-syotteet-tilat (r/atom {})
			teemaoverridet-tilat (r/atom {})
			teemaoverride-syotteet-tilat (r/atom {})]
		(fn []
			(let [viesti-ryhman-tiedot viesti/kehityssivun-viesti-esimerkit
				  yhteinen-teema (or @teemaoverridet-tilat {})]
				[:div {:class "ui-komponenttien-tarkastelu-nakyma"
						 :data-cy "ui-komponenttien-tarkastelu-sivu"
						 :style (when (seq yhteinen-teema)
							(renderointi/muunna-tyylioverridet-inline-tyyliksi yhteinen-teema))}
				 [:section {:class "ui-komponenttien-tarkastelu-sivun-otsikko"}
					[:h1 "UI-komponenttien tarkastelu"]
					[:p "Tämä sivu kokoaa Harjan omia UI-komponentteja manuaalista tarkastelua varten."]
					[:p "Primitiivit ovat yksi osa kokonaisuutta, ja tällä sivulla voi nyt tarkastella viestejä, modaaleja, paneeleja ja välilehtiä."]
					[:p "Kortit näyttävät nyt erikseen myös sen, onko komponentti jo portattu pois Bootstrapista vai nojaako se vielä vanhaan toteutukseen."]
					[:p "Välilehtien kokeilukortissa voi nyt säätää sallittuja tyylimuuttujia, nähdä preview'n ja kopioida override-mapin yhdestä paikasta."]
					[:p "Lisäksi tällä sivulla voi nyt kokeilla jaettuja teeman perusasetuksia, jotka vaikuttavat useaan komponenttiin yhtä aikaa ennen tarkempia komponenttikohtaisia overrideja."]]
				 [renderointi/renderoi-yhteiset-teematokenit {:teemaoverridet-tilat teemaoverridet-tilat
										 :teemaoverride-syotteet-tilat teemaoverride-syotteet-tilat}
									 yhteiset-teematokenit]
				 [viestit/viestit-osio viesti-ryhman-tiedot]
				 [modaalit/modaalit-osio modaalin-tila]
				 [panelit/panelit-osio]
				 [valilehdet/valilehdet-osio {:valilehtien-tilat valilehtien-tilat
										 :tyylioverridet-tilat tyylioverridet-tilat
										 :tyylioverride-syotteet-tilat tyylioverride-syotteet-tilat}]]))))
