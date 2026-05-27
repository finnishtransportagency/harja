(ns harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.navigaatio
	(:require [harja.ui.bootstrap :as bootstrap]
					[harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.kehys :as kehys]
					[harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.renderointi :as renderointi]))

(defn- renderoi-dropdown-esikatselu [_ {:keys [parametrit]}]
	(let [{:keys [data-cy otsikko kohteet]} parametrit]
		[:div {:data-cy data-cy}
		 [bootstrap/dropdown otsikko
		  (mapv (fn [{:keys [teksti data-cy]}]
				  [:a {:href "#"
					   :data-cy data-cy}
				   teksti])
				kohteet)]]))

(defn- renderoi-navbar-esikatselu [_ {:keys [parametrit]}]
	(let [{:keys [data-cy luokka otsikko vasen-linkki oikea-linkki]} parametrit]
		[bootstrap/navbar {:data-cy data-cy
						   :luokka luokka}
		 [:span {:data-cy (str data-cy "-otsikko")} otsikko]
		 [:a {:href "#"} vasen-linkki]
		 :right
		 [:a {:href "#"} oikea-linkki]]))

(defn- navigaatio-komponentit []
	[{:id :navigaatio/dropdown
	  :nimi "Dropdown"
	  :kuvaus "Näyttää portatun `bs/dropdown`-polun tarkastelusivulla. Kortti lukitsee säilyneen public API:n `title` + `items`, uuden aukitilan ja vakaat `data-cy`-ankkurit ilman Bootstrapin `.dropdown*`-luokkia."
	  :data-cy "ui-komponenttien-tarkastelu-dropdown-kortti"
	  :bootstrap-tila :portattu
	  :bootstrap-kuvaus "Dropdown käyttää nyt Harjan omia `harja-dropdown*`-luokkia ja hallitsee avaus-tilansa itse. Nykyisen koodipohjahaun perusteella wrapper on käytössä vain tällä tarkastelusivulla, joten porttaus pysyy rajattuna eikä pakota käyttöpaikkamigraatioita samassa viipaleessa."
	  :ryhma :navigaatio
	  :luonne :shared-komponentti
	  :esikatselutapa :inline
	  :suunnittelukelpoinen? false
	  :slotit [:otsikko :kohteet]
	  :renderoi-esikatselu renderoi-dropdown-esikatselu
	  :oletusparametrit {:otsikko "Tyokalut"
						 :kohteet [{:teksti "Raportit"}
								  {:teksti "Asetukset"}]}
	  :parametrit {:data-cy "ui-komponenttien-tarkastelu-dropdown"
				   :otsikko "Tyokalut"
				   :kohteet [{:teksti "Raportit"
						   :data-cy "ui-komponenttien-tarkastelu-dropdown-raportit"}
						  {:teksti "Asetukset"
						   :data-cy "ui-komponenttien-tarkastelu-dropdown-asetukset"}]}}
	 {:id :navigaatio/navbar
	  :nimi "Navbar"
	  :kuvaus "Näyttää portatun `bs/navbar`-polun tarkastelusivulla. Kortti lukitsee uuden juuriankkurin, vasen/oikea-item-jaon ja mobiilivaihtajan ilman Bootstrapin `.navbar*`-luokkia."
	  :data-cy "ui-komponenttien-tarkastelu-navbar-kortti"
	  :bootstrap-tila :portattu
	  :bootstrap-kuvaus "Navbar käyttää nyt Harjan omaa `harja-navbar`-luokkaperhettä. `:luokka`, `:right`-sentinel, header-slot ja itemien `meta :context` säilyvät, mutta Bootstrapin `.navbar*`-markupi ei enää renderöidy wrapperista."
	  :ryhma :navigaatio
	  :luonne :shared-komponentti
	  :esikatselutapa :inline
	  :suunnittelukelpoinen? false
	  :slotit [:header :vasen :oikea]
	  :renderoi-esikatselu renderoi-navbar-esikatselu
	  :oletusparametrit {:otsikko "Harja"
					 :vasen-linkki "Urakat"
					 :oikea-linkki "Kirjaudu ulos"}
	  :parametrit {:data-cy "ui-komponenttien-tarkastelu-navbar"
				   :luokka "ui-komponenttien-tarkastelu-navbar"
				   :otsikko "Harja"
				   :vasen-linkki "Urakat"
				   :oikea-linkki "Kirjaudu ulos"}}])

(defn navigaatio-osio []
	[kehys/tarkastelu-osio {:otsikko "Navigaatio"
							 :kuvaus "Navigaatio-osion kautta lukitaan shared-komponenttien tarkastelupinta ennen käyttöpaikkakohtaisia muutoksia. Tällä sivulla voi nyt tarkastaa sekä portatun dropdownin aukitilan että portatun navbarin käyttäytymisen."
							 :data-cy "ui-komponenttien-tarkastelu-navigaatio-osio"
							 :sisalto [:div {:class "ui-komponenttien-tarkastelu-ruudukko"}
									   (renderointi/renderoi-komponentit nil (navigaatio-komponentit))]}])
