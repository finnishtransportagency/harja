(ns harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.alasvedot
	(:require [reagent.core :as r]
					[harja.ui.yleiset :as yleiset]
					[harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.kehys :as kehys]
					[harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.renderointi :as renderointi]))

(defn- renderoi-livi-pudotusvalikko-esikatselu [_ {:keys [parametrit]}]
	(r/with-let [valinta (r/atom nil)]
		(let [{:keys [data-cy otsikko vaihtoehdot optiot format-fn]} parametrit
			  formaatti (or format-fn str)]
			[:div {:data-cy data-cy}
			 [yleiset/pudotusvalikko otsikko
			  (merge
			   {:data-cy (str data-cy "-valikko")
			   :valinta @valinta
			   :valitse-fn #(reset! valinta %)
			   :format-fn formaatti}
			   optiot)
			  vaihtoehdot]
			 [:p {:data-cy (str data-cy "-valinta")}
			  (str "Valittu: " (if (some? @valinta)
							   (formaatti @valinta)
							   "ei valittu"))]])))

(defn- renderoi-alasveto-toiminnolla-esikatselu [_ {:keys [parametrit]}]
	(r/with-let [valittu (r/atom "Urakat")]
		(let [{:keys [data-cy vaihtoehdot]} parametrit]
			[:div {:data-cy data-cy}
			 [yleiset/alasveto-toiminnolla
			  (fn [{:keys [sulje]}]
				[:button {:type "button"
						  :class "nappi-toissijainen"
						  :data-cy (str data-cy "-toiminto")
						  :on-click sulje}
				 "Sulje"])
			  {:valittu @valittu
			   :valinnat vaihtoehdot
			   :valinta-fn #(reset! valittu %)
			   :formaatti-fn str}]
			 [:p {:data-cy (str data-cy "-valinta")}
			  (str "Valittu: " @valittu)]])))

(defn- alasveto-komponentit []
	[{:id :alasvedot/livi-pudotusvalikko
	  :nimi "Livi-pudotusvalikko"
	  :kuvaus "Nayttaa tuotantopolussa kaytetyn `harja.ui.yleiset/pudotusvalikko`-sopimuksen tarkastelusivulla. Kortti lukitsee nykyisen API:n ja Bootstrap-riippuvaiset `.dropdown*`-luokat ennen porttausviipaletta."
	  :data-cy "ui-komponenttien-tarkastelu-livi-pudotusvalikko-kortti"
	  :bootstrap-tila :bootstrap-riippuvainen
	  :bootstrap-kuvaus "Komponentti renderoi edelleen Bootstrapin `dropdown`- ja `dropdown-menu`-luokkia `harja.ui.yleiset`-tasolla. Tama on seuraava shared-porttauspolku."
	  :ryhma :alasvedot
	  :luonne :shared-komponentti
	  :esikatselutapa :inline
	  :suunnittelukelpoinen? false
	  :slotit [:otsikko :vaihtoehdot]
	  :renderoi-esikatselu renderoi-livi-pudotusvalikko-esikatselu
	  :oletusparametrit {:otsikko "Nakyma"
					 :vaihtoehdot ["Urakat" "Raportit" "Hallinta"]}
	  :parametrit {:data-cy "ui-komponenttien-tarkastelu-livi-pudotusvalikko"
			   :otsikko "Nakyma"
			   :vaihtoehdot ["Urakat" "Raportit" "Hallinta"]}}
	 {:id :alasvedot/livi-pudotusvalikko-vayla
	  :nimi "Livi-pudotusvalikko (vayla-tyyli)"
	  :kuvaus "Nayttaa saman shared-rungon `:vayla-tyyli?`-variantilla. Kortti varmistaa, etta porttaus ei riko vayla-polun kayttokokemusta."
	  :data-cy "ui-komponenttien-tarkastelu-livi-pudotusvalikko-vayla-kortti"
	  :bootstrap-tila :bootstrap-riippuvainen
	  :bootstrap-kuvaus "Vaikka nappityyli vaihtuu, alasvetolista nojaa edelleen `.dropdown-menu`-rakenteeseen."
	  :ryhma :alasvedot
	  :luonne :shared-komponentti
	  :esikatselutapa :inline
	  :suunnittelukelpoinen? false
	  :slotit [:otsikko :vaihtoehdot]
	  :renderoi-esikatselu renderoi-livi-pudotusvalikko-esikatselu
	  :oletusparametrit {:otsikko "Urakkatyyppi"
					 :vaihtoehdot ["Hoito" "Paallystys" "Valaistus"]}
	  :parametrit {:data-cy "ui-komponenttien-tarkastelu-livi-pudotusvalikko-vayla"
			   :otsikko "Urakkatyyppi"
			   :vaihtoehdot ["Hoito" "Paallystys" "Valaistus"]
			   :optiot {:vayla-tyyli? true}}}
	 {:id :alasvedot/livi-pudotusvalikko-ryhmitelty
	  :nimi "Livi-pudotusvalikko (ryhmitelty)"
	  :kuvaus "Nayttaa ryhmitellyn alasvetonakyman (`:ryhmittely`, `:nayta-ryhmat`). Tama vastaa tuotantopolun ryhmittelysopimusta."
	  :data-cy "ui-komponenttien-tarkastelu-livi-pudotusvalikko-ryhmitelty-kortti"
	  :bootstrap-tila :bootstrap-riippuvainen
	  :bootstrap-kuvaus "Ryhmittely rakentuu edelleen Bootstrap-pohjaisen alasvetolistan paalle."
	  :ryhma :alasvedot
	  :luonne :shared-komponentti
	  :esikatselutapa :inline
	  :suunnittelukelpoinen? false
	  :slotit [:otsikko :vaihtoehdot]
	  :renderoi-esikatselu renderoi-livi-pudotusvalikko-esikatselu
	  :oletusparametrit {:otsikko "Valitse tehtava"
					 :vaihtoehdot [{:nimi "Auraus" :ryhma :talvihoito}
						     {:nimi "Suolaus" :ryhma :talvihoito}
						     {:nimi "Niitto" :ryhma :kesahoito}]}
	  :parametrit {:data-cy "ui-komponenttien-tarkastelu-livi-pudotusvalikko-ryhmitelty"
			   :otsikko "Valitse tehtava"
			   :vaihtoehdot [{:nimi "Auraus" :ryhma :talvihoito}
				       {:nimi "Suolaus" :ryhma :talvihoito}
				       {:nimi "Niitto" :ryhma :kesahoito}]
			   :format-fn :nimi
			   :optiot {:ryhmittely :ryhma
				    :nayta-ryhmat [:talvihoito :kesahoito]
				    :ryhman-otsikko name}}}
	 {:id :alasvedot/alasveto-toiminnolla
	  :nimi "Alasveto toiminnolla"
	  :kuvaus "Nayttaa `harja.ui.yleiset/alasveto-toiminnolla`-polun. Kortti tekee nakyvaksi saman dropdown-rungon ja lisatoiminnon ennen rakennemuutoksia."
	  :data-cy "ui-komponenttien-tarkastelu-alasveto-toiminnolla-kortti"
	  :bootstrap-tila :bootstrap-riippuvainen
	  :bootstrap-kuvaus "Komponentti nojaa edelleen `dropdown`-runkoon ja `harja-alasvetolistaitemi`-rakenteeseen. Porttaus tehdaan vasta shared-rungon kautta."
	  :ryhma :alasvedot
	  :luonne :shared-komponentti
	  :esikatselutapa :inline
	  :suunnittelukelpoinen? false
	  :slotit [:toiminto]
	  :renderoi-esikatselu renderoi-alasveto-toiminnolla-esikatselu
	  :oletusparametrit {:vaihtoehdot ["Urakat" "Raportit" "Hallinta"]}
	  :parametrit {:data-cy "ui-komponenttien-tarkastelu-alasveto-toiminnolla"
			   :vaihtoehdot ["Urakat" "Raportit" "Hallinta"]}}])

(defn alasvedot-osio []
	[kehys/tarkastelu-osio {:otsikko "Alasvedot"
							 :kuvaus "Alasveto-osio lukitsee tuotantokaytossa olevan shared-rungon tarkastelupinnan ennen bootstrap-porttausta."
							 :data-cy "ui-komponenttien-tarkastelu-alasvedot-osio"
							 :sisalto [:div {:class "ui-komponenttien-tarkastelu-ruudukko"}
									   (renderointi/renderoi-komponentit nil (alasveto-komponentit))]}])
