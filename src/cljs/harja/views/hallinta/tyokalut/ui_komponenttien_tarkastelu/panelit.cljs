(ns harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.panelit
	(:require [harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.kehys :as kehys]
					[harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.renderointi :as renderointi]))

(defn- paneli-komponentit []
	[{:id :panelit/perus
	  :nimi "Peruspaneeli"
	  :kuvaus "Näyttää portatun `bs/panel`-polun otsikolla. Tarkoitus on tarkastaa uusi juuriankkuri sekä otsake- ja runkoluokat ilman Bootstrap-markupia."
	  :data-cy "ui-komponenttien-tarkastelu-paneli-kortti"
	  :bootstrap-tila :portattu
	  :bootstrap-kuvaus "Tämä käyttää nyt Harjan omia `harja-panel`-, `harja-panel-otsake`- ja `harja-panel-runko`-luokkia. Public API säilyi, mutta Bootstrapin panel-markup ei enää renderöidy tästä wrapperista."
	  :tyyppi :paneli
	  :ryhma :panelit
	  :luonne :shared-komponentti
	  :esikatselutapa :inline
	  :suunnittelukelpoinen? false
	  :slotit [:otsikko :sisalto]
	  :oletusparametrit {:otsikko "Peruspaneeli"
						 :sisalto-rivit ["Paneeli käyttää nyt Harjan omaa panel-rakennetta Bootstrap-markupin sijaan."
								 "Tässä kortissa voi tarkastaa uuden juuriankkurin sekä otsake- ja runkoluokat ennen käyttöpaikkakohtaista verifya."]}
	  :parametrit {:data-cy "ui-komponenttien-tarkastelu-paneli"
				   :otsikko "Peruspaneeli"
				   :sisalto-data-cy "ui-komponenttien-tarkastelu-paneli-sisalto"
				   :sisalto-rivit ["Paneeli käyttää nyt Harjan omaa panel-rakennetta Bootstrap-markupin sijaan."
							 "Tässä kortissa voi tarkastaa uuden juuriankkurin sekä otsake- ja runkoluokat ennen käyttöpaikkakohtaista verifya."]}}
	 {:id :panelit/ilman-otsikkoa
	  :nimi "Paneeli ilman otsikkoa"
	  :kuvaus "Tarkastaa toisen arityn, jossa otsikkoa ei anneta lainkaan. Tämä varmistaa, että portattu wrapper säilyttää optionaalisen otsikkosopimuksen."
	  :data-cy "ui-komponenttien-tarkastelu-paneli-ilman-otsikkoa-kortti"
	  :bootstrap-tila :portattu
	  :bootstrap-kuvaus "Portattu wrapper jättää yhä otsakelohkon pois, jos otsikkoa ei anneta, mutta käyttää rungossa Harjan omaa `harja-panel-runko`-luokkaa."
	  :tyyppi :paneli
	  :ryhma :panelit
	  :luonne :shared-komponentti
	  :esikatselutapa :inline
	  :suunnittelukelpoinen? false
	  :slotit [:sisalto]
	  :oletusparametrit {:sisalto-rivit ["Tämä kortti näyttää nykyisen polun ilman otsikkoa."
								 "Portattu wrapper säilyttää tämän optionaalisen otsikkosopimuksen ilman Bootstrapin panel-heading-rakennetta."]}
	  :parametrit {:data-cy "ui-komponenttien-tarkastelu-paneli-ilman-otsikkoa"
				   :sisalto-data-cy "ui-komponenttien-tarkastelu-paneli-ilman-otsikkoa-sisalto"
				   :sisalto-rivit ["Tämä kortti näyttää nykyisen polun ilman otsikkoa."
							 "Portattu wrapper säilyttää tämän optionaalisen otsikkosopimuksen ilman Bootstrapin panel-heading-rakennetta."]}}])

(defn panelit-osio []
	[kehys/tarkastelu-osio {:otsikko "Panelit"
							 :kuvaus "Panelit on nyt portattu shared `panel`-wrapperissa pois Bootstrap-markupista. Tällä sivulla voi tarkastaa uuden juuriankkurin ja optionaalisen otsikkosopimuksen ennen käyttöpaikkakohtaisia jatkomuutoksia."
							 :data-cy "ui-komponenttien-tarkastelu-panelit-osio"
							 :sisalto [:div {:class "ui-komponenttien-tarkastelu-ruudukko"}
									   (renderointi/renderoi-komponentit nil (paneli-komponentit))]}])
