(ns harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.panelit
	(:require [reagent.core :as r]
					[harja.ui.bootstrap :as bootstrap]
					[harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.kehys :as kehys]
					[harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.renderointi :as renderointi]))

(defn- renderoi-dropdown-panel-esikatselu [_ {:keys [parametrit]}]
	(r/with-let [auki? (r/atom false)]
		(let [{:keys [data-cy otsikko sisalto-rivit tyyli]} parametrit]
			[bootstrap/dropdown-panel {:open auki?
									  :style tyyli
									  :data-cy data-cy}
			 otsikko
			 [:div
			  (into [:<>] (map (fn [rivi] [:p rivi]) sisalto-rivit))]])))

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
							 "Portattu wrapper säilyttää tämän optionaalisen otsikkosopimuksen ilman Bootstrapin panel-heading-rakennetta."]}}
	 {:id :panelit/dropdown-panel
	  :nimi "Dropdown-panel"
	  :kuvaus "Näyttää portatun `bs/dropdown-panel`-polun tarkastelusivulla. Kortti lukitsee uuden juuriankkurin, vaihtajapainikkeen ja auki-sulje-käytöksen ilman Bootstrapin panel- tai glyphicon-markupia."
	  :data-cy "ui-komponenttien-tarkastelu-dropdown-panel-kortti"
	  :bootstrap-tila :portattu
	  :bootstrap-kuvaus "Dropdown-panel käyttää nyt Harjan omia `harja-dropdown-panel`- ja `harja-panel-*`-luokkia. `:style`-variantti ja ulkoinen open-state säilyvät, mutta vanha Bootstrap-markup ei enää renderöidy."
	  :ryhma :panelit
	  :luonne :shared-komponentti
	  :esikatselutapa :inline
	  :suunnittelukelpoinen? false
	  :slotit [:otsikko :sisalto]
	  :renderoi-esikatselu renderoi-dropdown-panel-esikatselu
	  :oletusparametrit {:tyyli :primary
					 :otsikko "Lisäasetukset"
					 :sisalto-rivit ["Dropdown-panel on nyt portattu pois Bootstrapin panel- ja glyphicon-markupista."
							   "Avaa kortti tarkastaaksesi uuden vaihtajapainikkeen, auki-sulje-tilan ja sisältörungon."]}
	  :parametrit {:data-cy "ui-komponenttien-tarkastelu-dropdown-panel"
				   :tyyli :primary
				   :otsikko "Lisäasetukset"
				   :sisalto-rivit ["Dropdown-panel on nyt portattu pois Bootstrapin panel- ja glyphicon-markupista."
							 "Avaa kortti tarkastaaksesi uuden vaihtajapainikkeen, auki-sulje-tilan ja sisältörungon."]}}])

(defn panelit-osio []
	[kehys/tarkastelu-osio {:otsikko "Panelit"
							 :kuvaus "Panelit on nyt portattu shared `panel`- ja `dropdown-panel`-wrappereissa pois Bootstrap-markupista. Tällä sivulla voi tarkastaa uuden juuriankkurin, optionaalisen otsikkosopimuksen ja dropdown-panelin vaihtajakäytöksen ennen käyttöpaikkakohtaisia jatkomuutoksia."
							 :data-cy "ui-komponenttien-tarkastelu-panelit-osio"
							 :sisalto [:div {:class "ui-komponenttien-tarkastelu-ruudukko"}
									   (renderointi/renderoi-komponentit nil (paneli-komponentit))]}])
