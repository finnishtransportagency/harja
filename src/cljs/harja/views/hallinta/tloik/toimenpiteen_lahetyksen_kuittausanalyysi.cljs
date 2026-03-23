(ns harja.views.hallinta.tloik.toimenpiteen-lahetyksen-kuittausanalyysi
	(:require [clojure.string :as str]
						[reagent.core :as r]
						[harja.tiedot.hallinta.tloik.toimenpiteen-lahetyksen-kuittausanalyysi :as tiedot]
						[harja.pvm :as pvm]
						[harja.ui.grid :refer [grid]]
						[harja.ui.yleiset :refer [ajax-loader]]))

(defn- nayta-uniikit-ulkoiset-idt [rivi]
	(let [idt (:uniikit-ulkoiset-idt rivi)
				naytettavat (take 3 idt)]
		(str (count idt)
				 (when (seq naytettavat)
					 (str " (" (str/join ", " naytettavat)
								(when (> (count idt) (count naytettavat)) ", ...")
								")")))))

(defn- nayta-esimerkkitapahtumat [rivi]
	(->> (:esimerkkitapahtumat rivi)
			 (map #(str (:tapahtuma-id %)
									" ("
									(pvm/pvm-aika-sek (:alkanut %))
									")"))
			 (str/join ", ")))

(defn- nayta-kanava [kanava]
	(let [kanava (if (keyword? kanava)
						(name kanava)
						kanava)]
		(case kanava
			"sms" "SMS"
			"sahkoposti" "Sähköposti"
			"harja" "Harja"
			"ulkoinen_jarjestelma" "Ulkoinen järjestelmä"
			"tuntematon" "Tuntematon"
			kanava)))

(defn- kuittausanalyysin-selite []
	[:div
	 [:div "Huom. tämä osio käyttää vain valittua aikaväliä ja valittua integraatiota. Muut tarkemmat hakuehdot eivät vaikuta tähän näkymään."]
	 [:div "Taulukko näyttää kuittausryhmät kanavittain. Duplikaatit ja lähetysvirheet näytetään erillisinä sarakkeina, ja uniikit kuittaajat on erotettu duplikaattien viereen."]])

(defn epaillyt-duplikaattikuittaukset-osio []
	(r/with-let [nayta-duplikaattikuittaukset? (r/atom false)]
		(let [vastaus @tiedot/epaillyt-duplikaattikuittaukset
				sisalto (cond
							(= :ei-kaytossa vastaus)
							[:div "Hae tiedot nähdäksesi kuittausanalyysin valitulle aikavälille."]

							(= tiedot/duplikaattikuittaukset-ladataan vastaus)
							[ajax-loader "Haetaan kuittausanalyysiä"]

							(= tiedot/duplikaattikuittaukset-epaonnistui vastaus)
							[:div.integraatioloki-virhe "Kuittausanalyysin haku epäonnistui. Hae tiedot uudelleen."]

							(empty? (:ryhmat vastaus))
							[:div
							 [:div "Valitulla aikavälillä ei löytynyt duplikaatteja eikä lähetysvirheitä."]
							 (when (pos? (:ohitetut-rivit vastaus))
								 [:div (str "Ohitettu ilman liiketoiminta-avainta: " (:ohitetut-rivit vastaus) " riviä.")])
							 (when (:katkaistu vastaus)
								 [:div "Haku katkaistiin turvarajaan, joten kaikki rivit eivät ole mukana."])]

							:else
							[:div
							 (when (pos? (:ohitetut-rivit vastaus))
								 [:div (str "Ohitettu ilman liiketoiminta-avainta: " (:ohitetut-rivit vastaus) " riviä.")])
							 (when (:katkaistu vastaus)
								 [:div "Haku katkaistiin turvarajaan, joten näkymä voi olla osittainen."])
							 [grid
							  {:otsikko ""
							   :voi-muokata? false
							   :tunniste (juxt :ilmoitusid :kuittaustyyppi :kanava)}
							  [{:otsikko "IlmoitusId" :nimi :ilmoitusid :leveys "8%" :tyyppi :numero}
							   {:otsikko "Tyyppi" :nimi :kuittaustyyppi :leveys "10%" :tyyppi :string}
									   {:otsikko "Kanava" :nimi :kanava :leveys "8%" :fmt nayta-kanava}
							   {:otsikko "Duplikaatteja" :nimi :duplikaatteja :leveys "8%" :tyyppi :numero}
							   {:otsikko "Uniikkeja kuittaajia" :nimi :uniikit-kuittaajat :leveys "8%" :tyyppi :numero}
							   {:otsikko "Lähetysvirheitä" :nimi :kertyneet-lahetysvirheet :leveys "8%" :tyyppi :numero}
							   {:otsikko "Ensimmäinen" :nimi :ensimmainen-alkanut :leveys "12%" :fmt pvm/pvm-aika-sek}
							   {:otsikko "Viimeisin" :nimi :viimeisin-alkanut :leveys "12%" :fmt pvm/pvm-aika-sek}
							   {:otsikko "Uniikit viesti-id:t" :nimi :uniikit-ulkoiset-idt :leveys "12%" :tyyppi :komponentti
							    :komponentti nayta-uniikit-ulkoiset-idt}
							   {:otsikko "Esimerkkitapahtumat" :nimi :esimerkkitapahtumat :leveys "14%" :tyyppi :komponentti
							    :komponentti nayta-esimerkkitapahtumat}]
							  (:ryhmat vastaus)]])]
			[:div
			 [:h5 {:on-click #(swap! nayta-duplikaattikuittaukset? not)
						 :style {:cursor "pointer"}}
				"Toimenpiteen lähetyksen kuittausanalyysi <Avaa klikkaamalla>"]
			 (when @nayta-duplikaattikuittaukset?
				[:div
				 [kuittausanalyysin-selite]
				 sisalto])])))
