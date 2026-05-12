(ns harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.modaalit
	(:require [harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.kehys :as kehys]
					[harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.renderointi :as renderointi]))

(defn modaali-komponentit []
	[{:id :modaalit/perus
	  :nimi "Perusmodaali"
	  :kuvaus "Avaa yksinkertainen modaali, jolla voi tarkistaa otsikon, rungon, sulkemisen ja footerin peruskäyttäytymistä."
	  :data-cy "ui-komponenttien-tarkastelu-modaali-kortti"
	  :tyyppi :modaali
	  :ryhma :modaalit
	  :luonne :interaktiivinen
	  :esikatselutapa :overlay
	  :suunnittelukelpoinen? false
	  :slotit [:otsikko :sisalto :footer]
	  :oletusparametrit {:otsikko "Perusmodaali"
						 :avaus-teksti "Avaa perusmodaali"
						 :footer-teksti "Sulje"
						 :sisalto-rivit ["Tämä modaali on tarkoitettu tarkastamaan Harjan yhteisen modal-komponentin perusulkoasu ja sulkemispolut."
								   "Avaa tämä näkymä vain manuaalista UI-tarkastelua varten."]}
	  :parametrit {:otsikko "Perusmodaali"
				   :avaus-teksti "Avaa perusmodaali"
				   :footer-teksti "Sulje"
				   :sisalto-rivit ["Tämä modaali on tarkoitettu tarkastamaan Harjan yhteisen modal-komponentin perusulkoasu ja sulkemispolut."
						      "Avaa tämä näkymä vain manuaalista UI-tarkastelua varten."]}}])

(defn modaalit-osio [modaalin-tila]
	[kehys/tarkastelu-osio {:otsikko "Modaalit"
							 :kuvaus "Modaaleja voi avata tältä sivulta manuaalista tarkastelua varten. Tarkoitus on näyttää perusrakenne, ei tarjota tuotantokäyttöistä demojärjestelmää."
							 :data-cy "ui-komponenttien-tarkastelu-modaalit-osio"
							 :sisalto [:div {:class "ui-komponenttien-tarkastelu-ruudukko"}
									   (renderointi/renderoi-komponentit {:modaalin-tila modaalin-tila} (modaali-komponentit))]}])
