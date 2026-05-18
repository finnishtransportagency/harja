(ns harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.viestit
	(:require [harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.kehys :as kehys]
					[harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.renderointi :as renderointi]))

(defn viesti-komponentit [esimerkit]
	(let [teemaviesti {:id :teema-perusviesti
					:nimi "Teemoitu perusviesti"
					:kuvaus "Neutraali perusviesti näyttää suoremmin yhteisten teemamuuttujien pinta-, reuna- ja tekstivaikutuksen kuin statusvariantit."
					:data-cy "primitive-viesti-teema"
					:bootstrap-tila :portattu
					:bootstrap-kuvaus "Tämä viesti käyttää samaa primitive-runkoa kuin muutkin viestit, mutta ilman statusvariantin omia värejä."
					:tyyppi :viesti
					:ryhma :viestit
					:luonne :primitiivi
					:esikatselutapa :inline
					:suunnittelukelpoinen? true
					:slotit [:sisalto]
					:oletusparametrit {:sisalto "Tämä perusviesti näyttää yhteiset teematokenit ilman statusvariantin omia värejä."}
					:parametrit {:sisalto "Tämä perusviesti näyttää yhteiset teematokenit ilman statusvariantin omia värejä."}}]
		(cons teemaviesti
			  (map (fn [{:keys [id otsikko kuvaus data-cy luokka viesti]}]
					 {:id id
					  :nimi otsikko
					  :kuvaus kuvaus
					  :data-cy data-cy
					  :bootstrap-tila :portattu
					  :bootstrap-kuvaus "Tämä viesti renderöi Harjan omia primitive-luokkia eikä enää nojaa Bootstrapin alert- tai modal-luokkiin."
					  :tyyppi :viesti
					  :ryhma :viestit
					  :luonne :primitiivi
					  :esikatselutapa :inline
					  :suunnittelukelpoinen? true
					  :slotit [:sisalto]
					  :oletusparametrit {:variantti luokka
									:sisalto viesti}
					  :parametrit {:variantti luokka
							   :sisalto viesti}})
				 esimerkit))))

(defn- viesti-ryhma [{:keys [otsikko kuvaus esimerkit data-cy]}]
	[kehys/tarkastelu-osio {:otsikko otsikko
							 :kuvaus kuvaus
							 :otsikkotagi :h3
							 :data-cy data-cy
							 :sisalto [:div {:class "ui-komponenttien-tarkastelu-ruudukko"}
									   (renderointi/renderoi-komponentit nil (viesti-komponentit esimerkit))]}])

(defn viestit-osio [{:keys [otsikko kuvaus] :as viesti-ryhman-tiedot}]
	[kehys/tarkastelu-osio {:otsikko "Viestit"
						 :kuvaus (str "Ensimmäinen tarkasteltava komponenttiosio alkaa viesteistä. Ryhmä: " otsikko ". " kuvaus " Mukana on myös neutraali perusviesti, jotta yhteisten teemamuuttujien vaikutus näkyy ilman statusvarianttien omia värejä. Jokaisessa kortissa näytetään myös Bootstrap-porttauksen tila.")
							 :sisalto [viesti-ryhma viesti-ryhman-tiedot]}])
