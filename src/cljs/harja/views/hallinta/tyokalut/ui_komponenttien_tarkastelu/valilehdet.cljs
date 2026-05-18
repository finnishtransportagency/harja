(ns harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.valilehdet
	(:require [harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.kehys :as kehys]
					[harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.renderointi :as renderointi]))

(def ^:private tabs-tyylimuuttujat
	[{:avain :harja-tabs-border-color
	  :nimi "Reunan vari"
	  :tyyppi :color
	  :oletus "#afafaf"}
	 {:avain :harja-tabs-radius
	  :nimi "Kulman sade"
	  :tyyppi :text
	  :esimerkki "0.25rem"
	  :oletus "0.25rem"}
	 {:avain :harja-tabs-gap
	  :nimi "Vali tabien valissa"
	  :tyyppi :text
	  :esimerkki "0.125rem"
	  :oletus "0.125rem"}
	 {:avain :harja-tabs-padding-pysty
	  :nimi "Padding pysty"
	  :tyyppi :text
	  :esimerkki "0.625rem"
	  :oletus "0.625rem"}
	 {:avain :harja-tabs-padding-vaaka
	  :nimi "Padding vaaka"
	  :tyyppi :text
	  :esimerkki "0.9375rem"
	  :oletus "0.9375rem"}
	 {:avain :harja-tabs-taso1-passiivinen-tausta
	  :nimi "Passiivisen tabin tausta"
	  :tyyppi :color
	  :oletus "#0066cc"}
	 {:avain :harja-tabs-taso1-passiivinen-teksti
	  :nimi "Passiivisen tabin teksti"
	  :tyyppi :color
	  :oletus "#ffffff"}
	 {:avain :harja-tabs-taso1-passiivinen-hover-tausta
	  :nimi "Passiivisen hover-tausta"
	  :tyyppi :color
	  :oletus "#0088cc"}
	 {:avain :harja-tabs-taso1-aktiivinen-tausta
	  :nimi "Aktiivisen tabin tausta"
	  :tyyppi :color
	  :oletus "#fafafa"}
	 {:avain :harja-tabs-taso1-aktiivinen-teksti
	  :nimi "Aktiivisen tabin teksti"
	  :tyyppi :color
	  :oletus "#000000"}])

(defn- valilehti-komponentit []
	[{:id :valilehdet/perus
	  :nimi "Perusvälilehdet"
	  :kuvaus "Näyttää `tabs`-tyylin peruspolun kahdella välilehdellä. Tarkoitus on lukita käyttöesimerkki ja vakaat selektorit ennen varsinaista porttausta."
	  :data-cy "ui-komponenttien-tarkastelu-valilehti-kortti"
	  :bootstrap-tila :portattu
	  :bootstrap-kuvaus "Tämä käyttää nyt Harjan omia tabs-luokkia. Public API ja olemassa olevat data-cy-ankkurit säilyivät, mutta Bootstrapin nav-, nav-tabs-, nav-pills- ja active-luokat eivät enää renderöidy."
	  :tyyppi :valilehdet
	  :ryhma :valilehdet
	  :luonne :shared-komponentti
	  :esikatselutapa :inline
	  :suunnittelukelpoinen? true
	  :tyylimuuttujat tabs-tyylimuuttujat
	  :slotit [:otsikko :sisalto]
	  :oletusparametrit {:tyyli :tabs
							  :luokka "tabs-taso1"
							  :valilehdet [{:otsikko "Perustiedot"
											 :avain :perustiedot
											 :sisalto "Tämä sisältö näyttää aktiivisen välilehden perusrakenteen."}
										{:otsikko "Historia"
											 :avain :historia
											 :sisalto "Toinen välilehti varmistaa valinnan vaihtumisen ja sisällön renderöinnin."}]}
	  :parametrit {:tyyli :tabs
					 :luokka "tabs-taso1"
					 :data-cy "ui-komponenttien-tarkastelu-tabs"
					 :valilehdet [{:otsikko "Perustiedot"
									 :avain :perustiedot
									 :tabi-data-cy "ui-komponenttien-tarkastelu-tabs-perustiedot"
									 :sisalto "Tämä sisältö näyttää aktiivisen välilehden perusrakenteen."}
									{:otsikko "Historia"
									 :avain :historia
									 :tabi-data-cy "ui-komponenttien-tarkastelu-tabs-historia"
									 :sisalto "Toinen välilehti varmistaa valinnan vaihtumisen ja sisällön renderöinnin."}]}}
	 {:id :valilehdet/pillerit
	  :nimi "Pilleri-variantti"
	  :kuvaus "Näyttää saman API:n `pills`-tyylillä. Tämä antaa toisen käyttöesimerkin ilman että avataan vielä navbar- tai panel-polkuja. Tyylisäätökokeilu on toistaiseksi rajattu perusvälilehtiin."
	  :data-cy "ui-komponenttien-tarkastelu-pilleri-kortti"
	  :bootstrap-tila :portattu
	  :bootstrap-kuvaus "Myös `pills`-variantti renderöi nyt Harjan omia tabs-luokkia Bootstrap-luokkien sijaan."
	  :tyyppi :valilehdet
	  :ryhma :valilehdet
	  :luonne :shared-komponentti
	  :esikatselutapa :inline
	  :suunnittelukelpoinen? true
	  :slotit [:otsikko :sisalto]
	  :oletusparametrit {:tyyli :pills
							  :valilehdet [{:otsikko "Aktiivinen"
											 :avain :aktiivinen
											 :sisalto "Pilleri-variantin aktiivinen välilehti."}
										{:otsikko "Vaihtoehto"
											 :avain :vaihtoehto
											 :sisalto "Pilleri-variantin toinen vaihtoehto."}]}
	  :parametrit {:tyyli :pills
					 :data-cy "ui-komponenttien-tarkastelu-pills"
					 :valilehdet [{:otsikko "Aktiivinen"
									 :avain :aktiivinen
									 :tabi-data-cy "ui-komponenttien-tarkastelu-pills-aktiivinen"
									 :sisalto "Pilleri-variantin aktiivinen välilehti."}
									{:otsikko "Vaihtoehto"
									 :avain :vaihtoehto
									 :tabi-data-cy "ui-komponenttien-tarkastelu-pills-vaihtoehto"
									 :sisalto "Pilleri-variantin toinen vaihtoehto."}]}}])

(defn valilehdet-osio [konteksti]
	[kehys/tarkastelu-osio {:otsikko "Välilehdet"
							 :kuvaus "Tabs on nyt portattu pois Bootstrap-luokista. Mukana on yksi perusesimerkki ja yksi pilleri-variantti vakailla `data-cy`-ankkureilla."
							 :data-cy "ui-komponenttien-tarkastelu-valilehdet-osio"
							 :sisalto [:div {:class "ui-komponenttien-tarkastelu-ruudukko"}
									   (renderointi/renderoi-komponentit konteksti (valilehti-komponentit))]}])
