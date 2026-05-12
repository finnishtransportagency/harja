(ns harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu-nakyma
	(:require [reagent.core :as r]
					[harja.ui.primitiivit.viesti :as viesti]
					[harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.modaalit :as modaalit]
					[harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.viestit :as viestit]))

(defn ui-komponenttien-tarkastelu []
	(let [modaalin-tila (r/atom {:nakyvissa? false})]
		(fn []
			(let [viesti-ryhman-tiedot viesti/kehityssivun-viesti-esimerkit]
				[:div {:class "ui-komponenttien-tarkastelu-nakyma"
						 :data-cy "ui-komponenttien-tarkastelu-sivu"}
				 [:section {:class "ui-komponenttien-tarkastelu-sivun-otsikko"}
					[:h1 "UI-komponenttien tarkastelu"]
					[:p "Tämä sivu kokoaa Harjan omia UI-komponentteja manuaalista tarkastelua varten."]
					[:p "Primitiivit ovat yksi osa kokonaisuutta, ja tällä sivulla voi nyt tarkastella sekä viestejä että modaaleja."]]
				 [viestit/viestit-osio viesti-ryhman-tiedot]
				 [modaalit/modaalit-osio modaalin-tila]]))))
