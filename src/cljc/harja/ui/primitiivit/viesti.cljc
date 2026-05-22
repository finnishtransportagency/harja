(ns harja.ui.primitiivit.viesti)

(def flash-viestin-perusluokat
	{:overlay "harja-viesti-overlay"
	 :tausta "harja-viesti-tausta"
	 :runko "harja-viesti-runko"
	 :sisalto "harja-viesti"})

(def flash-viestin-variantit
	{:success "harja-viesti-onnistuminen"
	 :info "harja-viesti-info"
	 :warning "harja-viesti-varoitus"
	 :danger "harja-viesti-vaara"})

(defn flash-viestin-luokat [luokka]
	(assoc flash-viestin-perusluokat
		:variantti (get flash-viestin-variantit luokka "harja-viesti-info")))

(def kehityssivun-viesti-esimerkit
	{:id :viesti
	 :otsikko "Viesti"
	 :kuvaus "Ensimmäinen komponenttitarkastelun viestiryhmä Bootstrapin viestiluokat korvaavalle viestille."
	 :data-cy "primitive-viesti-ryhma"
	 :esimerkit [{:id :onnistuminen
								:otsikko "Onnistuminen"
								:kuvaus "Käytetään onnistuneen tallennuksen tai muun positiivisen palautteen näyttämiseen."
								:luokka :success
								:viesti "Tallennus onnistui ilman huomautuksia."
								:data-cy "primitive-viesti-onnistuminen"}
							 {:id :info
								:otsikko "Tieto"
								:kuvaus "Neutraali tieto ilman varoitustilaa."
								:luokka :info
								:viesti "Tietoja päivitetään taustalla."
								:data-cy "primitive-viesti-info"}
							 {:id :varoitus
								:otsikko "Varoitus"
								:kuvaus "Käytetään tilanteissa, joissa käyttäjän kannattaa tarkistaa syöte tai tila."
								:luokka :warning
								:viesti "Kaikkia tietoja ei voitu tallentaa."
								 :data-cy "primitive-viesti-varoitus"}
								 {:id :vaara
								 :otsikko "Virhe"
								 :kuvaus "Käytetään selkeän virhetilan tai epäonnistuneen operaation korostamiseen."
								 :luokka :danger
								 :viesti "Tallennus epäonnistui ja vaatii toimenpiteitä."
								 :data-cy "primitive-viesti-vaara"}]})
