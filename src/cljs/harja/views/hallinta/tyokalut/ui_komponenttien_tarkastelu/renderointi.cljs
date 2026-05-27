(ns harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.renderointi
	(:require [clojure.string :as clj-str]
					[reagent.core :as r]
					[harja.ui.bootstrap :as bootstrap]
					[harja.ui.modal :as modal]
					[harja.ui.primitiivit.viesti :as viesti]
					[harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.kehys :as kehys]))

(declare normalisoi-tyylioverride-arvo
		 validi-tyylioverride-arvo?
		 kopioi-leikepoydalle!)

(defn- komponentin-tyylioverride-avain [{:keys [id data-cy]}]
	(or id data-cy))

(defn- komponentin-aktiiviset-tyylioverridet [{:keys [tyylioverridet-tilat]}
										 {:keys [tyyli-overridet] :as komponentti}]
	(merge tyyli-overridet
		   (get (or (some-> tyylioverridet-tilat deref) {})
				(komponentin-tyylioverride-avain komponentti))))

(defn muunna-tyylioverridet-inline-tyyliksi [tyylioverridet]
	(clj->js
		(into {}
			  (map (fn [[avain arvo]]
					 [(str "--" (name avain)) arvo])
				  tyylioverridet))))

(defn- teema-data-cy [suffix]
	(str "ui-komponenttien-tarkastelu-teema-" suffix))

(defn- aktiiviset-teemaoverridet [{:keys [teemaoverridet-tilat]}]
	(or (some-> teemaoverridet-tilat deref)
		{}))

(defn- paivita-teemaoverridet! [teemaoverridet-tilat avain oletus uusi-arvo]
	(swap! teemaoverridet-tilat
		   (fn [nykyiset]
			 (let [paivitetyt (if (= uusi-arvo oletus)
						  (dissoc nykyiset avain)
						  (assoc nykyiset avain uusi-arvo))]
			   (or (not-empty paivitetyt) {})))))

(defn- paivita-teemaoverride-syote! [teemaoverride-syotteet-tilat avain uusi-arvo]
	(swap! teemaoverride-syotteet-tilat assoc avain uusi-arvo))

(defn- poista-teemaoverride-syote! [teemaoverride-syotteet-tilat avain]
	(swap! teemaoverride-syotteet-tilat dissoc avain))

(defn- teemaoverride-syote [teemaoverride-syotteet-tilat avain]
	(get (or (some-> teemaoverride-syotteet-tilat deref) {}) avain))

(defn- naytettava-teemaoverride-arvo [konteksti aktiiviset-teemaoverridet {:keys [avain oletus]}]
	(or (teemaoverride-syote (:teemaoverride-syotteet-tilat konteksti) avain)
		(get aktiiviset-teemaoverridet avain oletus)))

(defn- vahvista-teemaoverride! [konteksti {:keys [avain tyyppi oletus]}]
	(let [raakasyote (teemaoverride-syote (:teemaoverride-syotteet-tilat konteksti) avain)
		  normalisoitu (normalisoi-tyylioverride-arvo raakasyote)]
		(cond
			(nil? normalisoitu)
			(do
				(poista-teemaoverride-syote! (:teemaoverride-syotteet-tilat konteksti) avain)
				(paivita-teemaoverridet! (:teemaoverridet-tilat konteksti) avain oletus oletus))

			(validi-tyylioverride-arvo? tyyppi normalisoitu)
			(do
				(paivita-teemaoverridet! (:teemaoverridet-tilat konteksti) avain oletus normalisoitu)
				(poista-teemaoverride-syote! (:teemaoverride-syotteet-tilat konteksti) avain))

			:else nil)))

(defn- teemaoverride-virhe [konteksti {:keys [avain tyyppi esimerkki]}]
	(let [raakasyote (teemaoverride-syote (:teemaoverride-syotteet-tilat konteksti) avain)
		  normalisoitu (normalisoi-tyylioverride-arvo raakasyote)]
		(when (and normalisoitu
				   (not (validi-tyylioverride-arvo? tyyppi normalisoitu)))
			(str "Virheellinen arvo"
				 (when esimerkki
					(str ", kayta esimerkiksi " esimerkki))
				 "."))))

(defn renderoi-yhteiset-teematokenit [konteksti teematokenit]
	(let [aktiiviset-teemaoverridet (aktiiviset-teemaoverridet konteksti)
		  override-teksti (pr-str (into (sorted-map) aktiiviset-teemaoverridet))]
		[:section {:class "ui-komponenttien-tarkastelu-teemapaneeli"
			   :data-cy (teema-data-cy "paneeli")}
		 [:div {:class "ui-komponenttien-tarkastelu-tyylioverridet-otsikko"}
		  [:div
		   [:h3 "Teeman perusasetukset"]
		   [:p "Saada ensin jaettuja perustokeneita. Naita voi kayttaa useissa komponenteissa ennen komponenttikohtaisia overrideja."]]
		  [:div {:class "ui-komponenttien-tarkastelu-tyylioverridet-toiminnot"}
		   [:button {:type "button"
			 :class "nappi-toissijainen"
			 :data-cy (teema-data-cy "palauta")
			 :on-click #(do
					(reset! (:teemaoverridet-tilat konteksti) {})
					(reset! (:teemaoverride-syotteet-tilat konteksti) {}))}
			"Palauta oletukset"]
		   [:button {:type "button"
			 :class "nappi-toissijainen"
			 :data-cy (teema-data-cy "kopioi")
			 :on-click #(kopioi-leikepoydalle! override-teksti)}
			"Kopioi teemamap"]]]
		 [:div {:class "ui-komponenttien-tarkastelu-tyylioverridet-ruudukko"}
		  (doall
			(for [{:keys [avain nimi tyyppi oletus] :as teematokeni} teematokenit]
			  ^{:key (name avain)}
			  [:label {:class "ui-komponenttien-tarkastelu-tyylioverride-kentta"}
			   [:span {:class "ui-komponenttien-tarkastelu-tyylioverride-kentan-nimi"}
				nimi]
			   [:input {:class "ui-komponenttien-tarkastelu-tyylioverride-input"
					:type (case tyyppi
						:color "color"
						"text")
					:data-cy (teema-data-cy (str "token-" (name avain)))
					:value (naytettava-teemaoverride-arvo konteksti aktiiviset-teemaoverridet teematokeni)
					:on-change #(if (= tyyppi :color)
							(paivita-teemaoverridet! (:teemaoverridet-tilat konteksti)
								avain
								oletus
								(.. % -target -value))
							(paivita-teemaoverride-syote! (:teemaoverride-syotteet-tilat konteksti)
								avain
								(.. % -target -value)))
					:on-blur #(when (not= tyyppi :color)
							(vahvista-teemaoverride! konteksti teematokeni))
					:on-key-down #(when (and (not= tyyppi :color)
									 (= "Enter" (.-key %)))
							(vahvista-teemaoverride! konteksti teematokeni))}]
			   [:span {:class "ui-komponenttien-tarkastelu-tyylioverride-oletus"}
				(str "Oletus: " oletus)]
			   (when-let [virhe (teemaoverride-virhe konteksti teematokeni)]
				 [:span {:class "ui-komponenttien-tarkastelu-tyylioverride-virhe"}
				  virhe])]))]
		 [:div {:class "ui-komponenttien-tarkastelu-tyylioverride-teksti"}
		  [:label {:class "ui-komponenttien-tarkastelu-tyylioverride-kentta"}
		   [:span {:class "ui-komponenttien-tarkastelu-tyylioverride-kentan-nimi"}
			"Kopioitava teemamap"]
		   [:textarea {:class "ui-komponenttien-tarkastelu-tyylioverride-tekstialue"
				:data-cy (teema-data-cy "kopioitava-map")
				:readOnly true
				:value override-teksti}]]]]))

(defn- paivita-tyylioverride! [tyylioverridet-tilat komponentti avain oletus uusi-arvo]
	(let [komponenttiavain (komponentin-tyylioverride-avain komponentti)]
		(swap! tyylioverridet-tilat
			   (fn [tilat]
				 (let [nykyiset (get tilat komponenttiavain {})
					   paivitetyt (if (= uusi-arvo oletus)
							  (dissoc nykyiset avain)
							  (assoc nykyiset avain uusi-arvo))]
				   (if (empty? paivitetyt)
					 (dissoc tilat komponenttiavain)
					 (assoc tilat komponenttiavain paivitetyt)))))))

(defn- normalisoi-tyylioverride-arvo [arvo]
	(let [siivottu (some-> arvo clj-str/trim)]
		(when (seq siivottu)
			siivottu)))

(defn- validi-tyylioverride-arvo? [tyyppi arvo]
	(case tyyppi
		:color (boolean (re-matches #"#[0-9a-fA-F]{6}" arvo))
		:text (boolean (or (= "0" arvo)
					  (re-matches #"\d+(?:\.\d+)?(?:rem|px|em|%)" arvo)))
		:shadow (boolean (or (= "none" arvo)
							(re-matches #"(?i)(?:inset\s+)?(?:0|\d+(?:\.\d+)?(?:rem|px|em|%))(?:\s+(?:0|\d+(?:\.\d+)?(?:rem|px|em|%))){1,3}(?:\s+(?:rgba?\([^)]*\)|#[0-9a-fA-F]{6}))?" arvo)))
		true))

(defn- tyylioverride-syoteavain [komponentti avain]
	[(komponentin-tyylioverride-avain komponentti) avain])

(defn- tyylioverride-data-cy [komponentti suffix]
	(str (name (komponentin-tyylioverride-avain komponentti)) "-" suffix))

(defn- paivita-tyylioverride-syote! [tyylioverride-syotteet-tilat komponentti avain uusi-arvo]
	(swap! tyylioverride-syotteet-tilat assoc (tyylioverride-syoteavain komponentti avain) uusi-arvo))

(defn- poista-tyylioverride-syote! [tyylioverride-syotteet-tilat komponentti avain]
	(swap! tyylioverride-syotteet-tilat dissoc (tyylioverride-syoteavain komponentti avain)))

(defn- tyylioverride-syote [tyylioverride-syotteet-tilat komponentti avain]
	(get (or (some-> tyylioverride-syotteet-tilat deref) {})
		 (tyylioverride-syoteavain komponentti avain)))

(defn- vahvista-tyylioverride! [konteksti komponentti {:keys [avain tyyppi oletus]}]
	(let [raakasyote (tyylioverride-syote (:tyylioverride-syotteet-tilat konteksti) komponentti avain)
		  normalisoitu (normalisoi-tyylioverride-arvo raakasyote)]
		(cond
			(nil? normalisoitu)
			(do
				(poista-tyylioverride-syote! (:tyylioverride-syotteet-tilat konteksti) komponentti avain)
				(paivita-tyylioverride! (:tyylioverridet-tilat konteksti) komponentti avain oletus oletus))

			(validi-tyylioverride-arvo? tyyppi normalisoitu)
			(do
				(paivita-tyylioverride! (:tyylioverridet-tilat konteksti) komponentti avain oletus normalisoitu)
				(poista-tyylioverride-syote! (:tyylioverride-syotteet-tilat konteksti) komponentti avain))

			:else nil)))

(defn- tyylioverride-virhe [konteksti komponentti {:keys [avain tyyppi esimerkki]}]
	(let [raakasyote (tyylioverride-syote (:tyylioverride-syotteet-tilat konteksti) komponentti avain)
		  normalisoitu (normalisoi-tyylioverride-arvo raakasyote)]
		(when (and normalisoitu
				   (not (validi-tyylioverride-arvo? tyyppi normalisoitu)))
			(str "Virheellinen arvo"
				 (when esimerkki
					(str ", kayta esimerkiksi " esimerkki))
				 "."))))

(defn- naytettava-tyylioverride-arvo [konteksti komponentti aktiiviset-tyylioverridet {:keys [avain oletus]}]
	(or (tyylioverride-syote (:tyylioverride-syotteet-tilat konteksti) komponentti avain)
		(get aktiiviset-tyylioverridet avain oletus)))

(defn- poista-komponentin-tyylioverride-syotteet! [tyylioverride-syotteet-tilat komponentti]
	(let [komponenttiavain (komponentin-tyylioverride-avain komponentti)]
		(swap! tyylioverride-syotteet-tilat
			   (fn [tilat]
				 (into {}
				   (remove (fn [[[avain _] _]]
					   (= avain komponenttiavain))
					   tilat))))))

(defn- kopioi-leikepoydalle! [teksti]
	(some-> js/navigator (.-clipboard) (.writeText teksti)))

(defn- renderoi-tyylioverridet [konteksti {:keys [tyylimuuttujat] :as komponentti}]
	(when (seq tyylimuuttujat)
		(let [aktiiviset-tyylioverridet (komponentin-aktiiviset-tyylioverridet konteksti komponentti)
			  override-teksti (pr-str (into (sorted-map) aktiiviset-tyylioverridet))]
			[:section {:class "ui-komponenttien-tarkastelu-tyylioverridet"
				   :data-cy (str (name (komponentin-tyylioverride-avain komponentti)) "-tyylioverridet")}
			 [:div {:class "ui-komponenttien-tarkastelu-tyylioverridet-otsikko"}
			  [:div
			   [:h4 "Tyylioverridet"]
			   [:p "Saada arvoja, katso preview ja kopioi muuttunut override-map kehittajalle."]]
			  [:div {:class "ui-komponenttien-tarkastelu-tyylioverridet-toiminnot"}
			   [:button {:type "button"
				 :class "nappi-toissijainen"
				 :data-cy (tyylioverride-data-cy komponentti "palauta-tyylioverridet")
				 :on-click #(do
						 (swap! (:tyylioverridet-tilat konteksti)
							dissoc
							(komponentin-tyylioverride-avain komponentti))
						 (poista-komponentin-tyylioverride-syotteet! (:tyylioverride-syotteet-tilat konteksti) komponentti))}
				"Palauta oletukset"]
			   [:button {:type "button"
				 :class "nappi-toissijainen"
				 :data-cy (tyylioverride-data-cy komponentti "kopioi-tyylioverride-map")
				 :on-click #(kopioi-leikepoydalle! override-teksti)}
				"Kopioi override-map"]]]
			 [:div {:class "ui-komponenttien-tarkastelu-tyylioverridet-ruudukko"}
			  (doall
				(for [{:keys [avain nimi tyyppi oletus] :as tyylimuuttuja} tyylimuuttujat]
				  ^{:key (name avain)}
				  [:label {:class "ui-komponenttien-tarkastelu-tyylioverride-kentta"}
				   [:span {:class "ui-komponenttien-tarkastelu-tyylioverride-kentan-nimi"}
					nimi]
				   [:input {:class "ui-komponenttien-tarkastelu-tyylioverride-input"
						:data-cy (tyylioverride-data-cy komponentti (str "tyylioverride-" (name avain)))
						:type (case tyyppi
							:color "color"
							"text")
						:value (naytettava-tyylioverride-arvo konteksti komponentti aktiiviset-tyylioverridet tyylimuuttuja)
						:on-change #(if (= tyyppi :color)
								(paivita-tyylioverride! (:tyylioverridet-tilat konteksti)
									   komponentti
									   avain
									   oletus
									   (.. % -target -value))
								(paivita-tyylioverride-syote! (:tyylioverride-syotteet-tilat konteksti)
										komponentti
										avain
										(.. % -target -value)))
						:on-blur #(when (= tyyppi :text)
							   (vahvista-tyylioverride! konteksti komponentti tyylimuuttuja))
						:on-key-down #(when (and (= tyyppi :text)
										 (= "Enter" (.-key %)))
							   (vahvista-tyylioverride! konteksti komponentti tyylimuuttuja))}]
				   [:span {:class "ui-komponenttien-tarkastelu-tyylioverride-oletus"}
					(str "Oletus: " oletus)]
				   (when-let [virhe (tyylioverride-virhe konteksti komponentti tyylimuuttuja)]
					 [:span {:class "ui-komponenttien-tarkastelu-tyylioverride-virhe"}
					  virhe])]))]
			 [:div {:class "ui-komponenttien-tarkastelu-tyylioverride-teksti"}
			  [:label {:class "ui-komponenttien-tarkastelu-tyylioverride-kentta"}
			   [:span {:class "ui-komponenttien-tarkastelu-tyylioverride-kentan-nimi"}
				"Kopioitava override-map"]
			   [:textarea {:class "ui-komponenttien-tarkastelu-tyylioverride-tekstialue"
					:data-cy (tyylioverride-data-cy komponentti "kopioitava-override-map")
					:readOnly true
					:value override-teksti}]]]])))

(defn renderoi-viesti-parametreista [{:keys [data-cy parametrit]}]
	(let [{:keys [variantti sisalto]} parametrit
		  viestin-data-cy (when data-cy (str data-cy "-teema"))
		  viestiluokat (if variantti
					 (viesti/flash-viestin-luokat variantti)
					 (assoc viesti/flash-viestin-perusluokat :variantti nil))
		  viestin-luokka (:sisalto viestiluokat)
		  variantin-luokka (:variantti viestiluokat)]
		[:div {:class "ui-komponenttien-tarkastelu-viesti"}
		 [:div {:class (str viestin-luokka " " variantin-luokka)
				:data-cy viestin-data-cy}
		  sisalto]]))

(defn renderoi-modaalin-parametreista [{:keys [modaalin-tila]}
								 {:keys [avaus-teksti footer-teksti otsikko sisalto-rivit]}]
	(let [avaa-modali! #(swap! modaalin-tila assoc :nakyvissa? true)
			sulje-modali! #(swap! modaalin-tila assoc :nakyvissa? false)
			footer-nappi [:button {:type "button"
						 :class "nappi-toissijainen"
						 :data-cy "ui-komponenttien-tarkastelu-sulje-modaali"
						 :on-click sulje-modali!}
				  footer-teksti]]
		[:<>
		 [:div {:class "ui-komponenttien-tarkastelu-toiminnot"}
		  [:button {:type "button"
					 :class "nappi-toissijainen"
					 :data-cy "ui-komponenttien-tarkastelu-avaa-modaali"
					 :on-click avaa-modali!}
		   avaus-teksti]]
		 [modal/modal {:otsikko otsikko
					 :nakyvissa? (:nakyvissa? @modaalin-tila)
					 :sulje-fn sulje-modali!
					 :footer footer-nappi}
		  [:div {:data-cy "ui-komponenttien-tarkastelu-modaali-sisalto"}
		   (into [:<>] (map (fn [rivi] [:p rivi]) sisalto-rivit))]]]))

(defn renderoi-valilehdet-parametreista [{:keys [valilehtien-tilat]}
							  {:keys [tyyli luokka data-cy valilehdet]}]
	(r/with-let [paikallinen-aktiivinen-valilehti (r/atom (-> valilehdet first :avain))]
		(let [valilehti-id (or data-cy "ui-komponenttien-tarkastelu-tabs")
			  aktiivinen-valilehti (if valilehtien-tilat
							 (r/cursor valilehtien-tilat [valilehti-id])
							 paikallinen-aktiivinen-valilehti)]
		  [:div {:class "ui-komponenttien-tarkastelu-valilehdet"
				 :data-cy valilehti-id}
		   (into [bootstrap/tabs {:active aktiivinen-valilehti
								:style tyyli
								:classes luokka}]
				 (mapcat (fn [{:keys [otsikko avain sisalto tabi-data-cy]}]
				   [(cond-> (if (map? otsikko)
							 otsikko
							 {:teksti otsikko})
					 tabi-data-cy (assoc :data-cy tabi-data-cy))
					avain [:div {:class "ui-komponenttien-tarkastelu-valilehden-sisalto"
						  :data-cy (str valilehti-id "-" (name avain) "-sisalto")}
						sisalto]])
				 valilehdet))])))

(defn renderoi-panelin-parametreista [_ {:keys [data-cy otsikko sisalto-data-cy sisalto-rivit]}]
	(let [sisalto [:div {:data-cy sisalto-data-cy}
				 (into [:<>] (map (fn [rivi] [:p rivi]) sisalto-rivit))]]
		(if otsikko
		  [bootstrap/panel {:data-cy data-cy} otsikko sisalto]
		  [bootstrap/panel {:data-cy data-cy} sisalto])))

(defn- renderoi-komponentin-sisalto [konteksti {:keys [tyyppi parametrit renderoi-esikatselu] :as komponentti}]
	(cond
		renderoi-esikatselu (renderoi-esikatselu konteksti komponentti)
		(= tyyppi :viesti) (renderoi-viesti-parametreista komponentti)
		(= tyyppi :modaali) (renderoi-modaalin-parametreista konteksti parametrit)
		(= tyyppi :valilehdet) (renderoi-valilehdet-parametreista konteksti parametrit)
		(= tyyppi :paneli) (renderoi-panelin-parametreista konteksti parametrit)
		:else [:div {:class "ui-komponenttien-tarkastelu-virhe"}
			   "Komponentin esikatselua ei ole määritelty."]))

(defn renderoi-komponentti-kortti [konteksti {:keys [id nimi kuvaus data-cy] :as komponentti}]
	(let [aktiiviset-tyylioverridet (komponentin-aktiiviset-tyylioverridet konteksti komponentti)]
		[kehys/tarkastelu-kortti {:id id
							 :otsikko nimi
							 :kuvaus kuvaus
							 :data-cy data-cy
							 :lisatiedot (kehys/komponentin-porttaustieto komponentti)
							 :sisalto [:<>
									[:div {:class "ui-komponenttien-tarkastelu-esikatselu"
										   :data-cy (tyylioverride-data-cy komponentti "esikatselu")
										   :style (when (seq aktiiviset-tyylioverridet)
											(muunna-tyylioverridet-inline-tyyliksi aktiiviset-tyylioverridet))}
									 (renderoi-komponentin-sisalto konteksti komponentti)]
									(renderoi-tyylioverridet konteksti komponentti)]}]))

(defn renderoi-komponentit [konteksti komponentit]
	(into [:<>] (map #(renderoi-komponentti-kortti konteksti %) komponentit)))
