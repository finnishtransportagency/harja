(ns harja.ui.raportti
  "Harjan raporttielementtien HTML näyttäminen.

  Harjan raportit ovat Clojuren tietorakenteita, joissa käytetään
  tiettyä rakennetta ja tiettyjä avainsanoja. Nämä raportit annetaan
  eteenpäin moottoreille, jotka luovat tietorakenteen pohjalta raportin.
  Tärkeä yksityiskohta on, että raporttien olisi tarkoitus sisältää ns.
  raakaa dataa, ja antaa raportin formatoida data oikeaan muotoon sarakkeen :fmt
  tiedon perusteella.

  Tämä moottori luo selaimessa näytettävän raportin. Alla käytetään Harjan gridiä.
  Kuten muissakin raporteissa, tärkein metodi on :taulukko, jonne mm.
  voi lisätä tuen eri tavoilla formatoitaville sarakkeille."
  (:require [harja.ui.grid :as grid]
            [harja.ui.dom :as dom]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.liitteet :as liitteet]
            [harja.visualisointi :as vis]
            [harja.domain.raportointi :as raportti-domain]
            [harja.loki :refer [log]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.modal :as modal]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [harja.ui.aikajana :as aikajana]
            [harja.ui.ikonit :as ikonit]
            [clojure.string :as str]
            [harja.ui.kentat :as kentat]))

(defmulti muodosta-html
  "Muodostaa Reagent komponentin annetulle raporttielementille."
  (fn [elementti]
    (if (raportti-domain/raporttielementti? elementti)
      (first elementti)
      :vain-arvo)))

(defmethod muodosta-html :vain-arvo [arvo] arvo)

(defmethod muodosta-html :arvo [[_ {:keys [arvo desimaalien-maara fmt ryhmitelty? jos-tyhja] :as elementti}]]
  [:span (if-not (nil? arvo)
           (cond
             desimaalien-maara (fmt/desimaaliluku-opt arvo desimaalien-maara ryhmitelty?)
             fmt (fmt arvo)
             :else arvo)
           jos-tyhja)])

(defmethod muodosta-html :liitteet [[_ liitteet]]
  (liitteet/liitteet-numeroina liitteet))

(defmethod muodosta-html :arvo-ja-osuus [[_ {:keys [arvo osuus fmt]}]]
  [:span.arvo-ja-osuus
   [:span.arvo (if fmt (fmt arvo) arvo)]
   [:span " "]
   [:span.osuus (str "(" osuus "%)")]])

;; Tavallisesti raportin solujen tyylit tulevat rivitasolta ja HTML raporteissa yksittäisen solun tyyli annetaan luokka
;; määritteessä (:sarakkeen-luokka). Niinpä tämän elementin ainoa olemassaolon syy on se, että tätä vaaditaan PDF ja Excelraportoissa.
;; Tämä on siis identtinen :arvo-ja-yksikkö elementin kanssa, mutta sallii raportin toiminnan.
(defmethod muodosta-html :arvo-ja-yksikko-korostettu [[_ {:keys [arvo yksikko fmt desimaalien-maara ryhmitelty?]}]]
  [:span.arvo-ja-yksikko
   [:span.arvo (cond
                 desimaalien-maara (fmt/desimaaliluku-opt arvo desimaalien-maara ryhmitelty?)
                 fmt (fmt arvo)
                 :else arvo)]
   [:span.yksikko (str "\u00A0" yksikko)]])

(defmethod muodosta-html :arvo-ja-yksikko [[_ {:keys [arvo yksikko fmt desimaalien-maara ryhmitelty?]}]]
  [:span.arvo-ja-yksikko
   [:span.arvo (cond
                 desimaalien-maara (fmt/desimaaliluku-opt arvo desimaalien-maara ryhmitelty?)
                 fmt (fmt arvo)
                 :else arvo)]
   [:span.yksikko (str "\u00A0" yksikko)]])

(defmethod muodosta-html :arvo-ja-selite [[_ {:keys [arvo selite]}]]
  [:span.arvo-ja-yksikko
   [:span.arvo arvo]
   [:div.selite.small-caption selite]])

(defmethod muodosta-html :erotus-ja-prosentti [[_ {:keys [arvo prosentti desimaalien-maara ryhmitelty?]}]]
  (let [etuliite (cond
                   (neg? arvo) "- "
                   (zero? arvo) ""
                   :else "+ ")
        arvo (Math/abs arvo)
        prosentti (Math/abs prosentti)]
    [:span.erotus-ja-prosentti
     [:span.arvo (str etuliite (cond
                                 desimaalien-maara (fmt/desimaaliluku-opt arvo desimaalien-maara ryhmitelty?)
                                 :else arvo))]
     [:div.selite.small-caption
      {:style {:text-align :inherit}}
      (str "(" etuliite (fmt/prosentti-opt prosentti) ")")]]))

(defmethod muodosta-html :teksti-ja-info [[_ {:keys [arvo info]}]]
  [:span.teksti-ja-info
   [:span.arvo (str arvo "\u00A0")]
   [yleiset/tooltip {:suunta :oikea :leveys :levea
                     :wrapper-luokka "tooltip-wrapper"
                     :wrapperin-koko {:leveys 20 :korkeus 20}}
    [ikonit/harja-icon-status-info]
    info]])

(defmethod muodosta-html :varillinen-teksti
  ;; :varillinen-teksti elementtiä voidaan käyttää mm. virheiden näyttämiseen. Pyritään aina käyttämään
  ;; ennaltamääriteltyjä tyylejä, mutta jos on erikoistapaus missä halutaan käyttää itsemääriteltyä väriä,
  ;; voidaan käyttää avainta :itsepaisesti-maaritelty-oma-vari
  [[_ {:keys [arvo tyyli itsepaisesti-maaritelty-oma-vari fmt lihavoi? kustomi-tyyli]}]]
  (let [lihavoi (when lihavoi? {:font-weight "bold"})]
    [:span.varillinen-teksti
     [:span.arvo {:class kustomi-tyyli :style (merge lihavoi {:color (or itsepaisesti-maaritelty-oma-vari (raportti-domain/virhetyylit tyyli) "rgb(25,25,25)")})}
      (if fmt (fmt arvo) arvo)]]))

(defmethod muodosta-html :infopallura
  ;; :infopallura elementtiä käytetään näyttämään tooltip tyyppisessä infokentässä lisätietoja kohteesta
  [[_ {:keys [infoteksti]}]]
  [yleiset/wrap-if true
   [yleiset/tooltip {} :% infoteksti]
   [:span {:style {:padding-left "4px"}} (ikonit/livicon-info-sign)]])

(defn- formatoija-fmt-mukaan [fmt]
  (case fmt
    :kokonaisluku #(raportti-domain/yrita fmt/kokonaisluku-opt %)
    :numero #(raportti-domain/yrita fmt/desimaaliluku-opt % 2 true)
    :numero-3desim #(fmt/pyorista-ehka-kolmeen %)
    :prosentti #(raportti-domain/yrita fmt/prosentti-opt % 1)
    :prosentti-0desim #(raportti-domain/yrita fmt/prosentti-opt % 0)
    :raha #(raportti-domain/yrita fmt/euro-opt %)
    :pvm #(raportti-domain/yrita fmt/pvm-opt %)
    str))

(defn generoi-gridi [otsikko viimeinen-rivi-yhteenveto?
                     rivi-ennen
                     piilota-border?
                     raportin-tunniste
                     tyhja
                     korosta-rivit korostustyyli
                     oikealle-tasattavat-kentat vetolaatikot esta-tiivis-grid?
                     avattavat-rivit sivuttain-rullattava? ensimmainen-sarake-sticky?
                     sarakkeet data]
  (let [oikealle-tasattavat-kentat (or oikealle-tasattavat-kentat #{})]
    [grid/grid {:otsikko (or otsikko "")
                :tunniste (fn [rivi]
                            (str "raportti_rivi_"
                              (or (::rivin-indeksi rivi)
                                (hash rivi))))
                :rivi-ennen rivi-ennen
                :avattavat-rivit avattavat-rivit
                :piilota-toiminnot? true
                :sivuttain-rullattava? sivuttain-rullattava?
                :ensimmainen-sarake-sticky? ensimmainen-sarake-sticky?
                :esta-tiivis-grid? esta-tiivis-grid?
                :piilota-border? piilota-border?
                :raportin-tunniste raportin-tunniste}
     (into []
       (map-indexed
         (fn [i sarake]
           (let [raporttielementteja? (raportti-domain/sarakkeessa-raporttielementteja? i data)
                 format-fn (formatoija-fmt-mukaan (:fmt sarake))]
             (merge
               {:hae #(get % i)
                :leveys (:leveys sarake)
                :otsikko (:otsikko sarake)
                :reunus (:reunus sarake)
                :pakota-rivitys? (:pakota-rivitys? sarake)
                :otsikkorivi-luokka (str (:otsikkorivi-luokka sarake)
                                      (case (:tasaa-otsikko sarake)
                                        :keskita " grid-header-keskita"
                                        :oikea " grid-header-oikea"
                                        ""))
                :solun-luokka (fn [arvo _rivi]
                                   ;; Jos rivi on tässä nimiavaruudessa määritetty komponentti, rivin optioissa voi
                                   ;; olla avain :varoitus?, jolloin piirretään solu punaisella taustalla ja tekstillä.
                                (str
                                  (when (:varoitus? (and (vector? arvo) (second arvo)))
                                    " solu-varoitus ")
                                  (when (:korosta-hennosti? (and (vector? arvo) (second arvo)))
                                    " hennosti-korostettu-solu ")
                                  (when (true? (:ala-korosta? (and (vector? arvo) (second arvo))))
                                    " solun-korostus-estetty ")))
                :luokka (:sarakkeen-luokka sarake)
                :nimi (str "sarake" i)
                :fmt format-fn
                   ;; Valtaosa raporttien sarakkeista on puhdasta tekstiä, poikkeukset komponentteja
                :tyyppi (cond
                          (= (:tyyppi sarake) :vetolaatikon-tila) :vetolaatikon-tila
                          (= (:tyyppi sarake) :avattava-rivi) :avattava-rivi
                          raporttielementteja? :komponentti
                          :else :string)
                :tasaa (if (or (oikealle-tasattavat-kentat i)
                             (raportti-domain/numero-fmt? (:fmt sarake)))
                         :oikea
                         (:tasaa sarake))}
               (when raporttielementteja?
                 {:komponentti
                  (fn [rivi]
                    (let [elementti (get rivi i)
                          liite? (if (vector? elementti)
                                   (= :liitteet (first elementti))
                                   false)] ;; Normaalisti komponenteissa toinen elementti on mappi, mutta liitteissä vektori.
                      (muodosta-html
                        (if (and (raportti-domain/formatoi-solu? elementti) (not liite?))
                          (raportti-domain/raporttielementti-formatterilla elementti
                            formatoija-fmt-mukaan
                            (:fmt sarake))
                          elementti))))}))))
         sarakkeet))
     (if (empty? data)
       [(grid/otsikko (or tyhja "Ei tietoja"))]
       (let [viimeinen-rivi (last data)]
         (into []
           (map-indexed (fn [index rivi]
                          (if-let [otsikko (:otsikko rivi)]
                            (grid/otsikko otsikko)
                            (let [[rivi optiot]
                                  (if (map? rivi)
                                    [(:rivi rivi) rivi]
                                    [rivi {}])
                                  isanta-rivin-id (:isanta-rivin-id optiot)
                                  lihavoi? (:lihavoi? optiot)
                                  korosta? (:korosta? optiot)
                                  korosta-hennosti? (:korosta-hennosti? optiot)
                                  korosta-harmaa? (:korosta-harmaa? optiot)
                                  valkoinen? (:valkoinen? optiot)
                                  rivin-luokka (:rivin-luokka optiot)
                                  mappina (assoc
                                            (zipmap (range (count sarakkeet))
                                              rivi)
                                            ::rivin-indeksi index)]
                              (cond-> mappina
                                (and viimeinen-rivi-yhteenveto?
                                  (= viimeinen-rivi rivi))
                                (assoc :yhteenveto true)

                                korosta-hennosti?
                                (assoc :korosta-hennosti true)

                                korosta-harmaa?
                                (assoc :korosta-harmaa true)

                                valkoinen?
                                (assoc :valkoinen true)

                                (or korosta? (when korosta-rivit (korosta-rivit index)))
                                (assoc :korosta true)

                                lihavoi?
                                (assoc :lihavoi true)

                                rivin-luokka
                                (assoc :rivin-luokka rivin-luokka)

                                isanta-rivin-id
                                (assoc :isanta-rivin-id isanta-rivin-id))))))
           data)))]))

(defmethod muodosta-html :taulukko [[_ {:keys [otsikko viimeinen-rivi-yhteenveto?
                                               rivi-ennen
                                               piilota-border?
                                               raportin-tunniste
                                               tyhja
                                               korosta-rivit korostustyyli
                                               oikealle-tasattavat-kentat vetolaatikot esta-tiivis-grid?
                                               avattavat-rivit sivuttain-rullattava? ensimmainen-sarake-sticky?]}
                                     
                                     sarakkeet data]]
  (generoi-gridi otsikko viimeinen-rivi-yhteenveto?
                 rivi-ennen
                 piilota-border?
                 raportin-tunniste
                 tyhja
                 korosta-rivit korostustyyli
                 oikealle-tasattavat-kentat vetolaatikot esta-tiivis-grid?
                 avattavat-rivit sivuttain-rullattava? ensimmainen-sarake-sticky?
                 sarakkeet data)
  )

(defmethod muodosta-html :otsikko-title [[_ teksti]]
  [:h1 teksti])

(defmethod muodosta-html :otsikko-heading [[_ teksti]]
  [:h2 {:style {:font-size "1.25rem"}} teksti])

(defmethod muodosta-html :otsikko [[_ teksti]]
  [:h3 teksti])

(defmethod muodosta-html :otsikko-heading-small [[_ teksti]]
  [:h4 {:style {:font-size "1rem"}} teksti])

(defmethod muodosta-html :jakaja [_]
  [:hr {:style {:margin-top "30px"
                :margin-bottom "30px"}}])

(defmethod muodosta-html :otsikko-kuin-pylvaissa [[_ teksti]]
  [:h3 teksti])

(defmethod muodosta-html :teksti [[_ teksti {:keys [vari infopallura rivita?]}]]
  [:div {:style (merge
                {:color (when vari vari)}
                (when rivita? {:white-space "pre-line"}))}
   teksti
   (when infopallura (muodosta-html [:infopallura infopallura]))])

(defmethod muodosta-html :teksti-paksu [[_ teksti {:keys [vari infopallura]}]]
  [:div {:style {:font-weight 700
               :color (when vari vari)}} teksti
   (when infopallura (muodosta-html [:infopallura infopallura]))])

(defmethod muodosta-html :varoitusteksti [[_ teksti]]
  (muodosta-html [:teksti teksti {:vari "#dd0000"}]))

(defmethod muodosta-html :infolaatikko [[_ teksti {:keys [tyyppi toissijainen-viesti leveys rivita?]}]]
  (let [tyyppi (or tyyppi :neutraali)]
    [:div {:style (merge
                    {:margin-bottom "1rem"}
                    (when rivita? {:white-space "pre-line"}))}
     [yleiset/info-laatikko tyyppi teksti toissijainen-viesti leveys teksti]]))


(defmethod muodosta-html :pylvaat [[_ {:keys [otsikko vari fmt piilota-arvo? legend]} pylvaat]]
  (let [w (int (* 0.85 @dom/leveys))
        h (int (/ w 2.9))]
    [:div.pylvaat
     [:h3 otsikko]
     [vis/bars {:width         w
                :height        h
                :format-amount (or fmt str)
                :hide-value?   piilota-arvo?
                :legend legend
                }
      pylvaat]]))

(defmethod muodosta-html :piirakka [[_ {:keys [otsikko]} data]]
  [:div.pylvaat
   [:h3 otsikko]
   [vis/pie
    {:width 230 :height 150 :radius 60 :show-text :percent :show-legend true}
    data]])

(defmethod muodosta-html :yhteenveto [[_ otsikot-ja-arvot]]
  (apply yleiset/taulukkotietonakyma {}
         (mapcat identity otsikot-ja-arvot)))

(defmethod muodosta-html :raportti [[_ raportin-tunnistetiedot & sisalto]]
  (log "muodosta html raportin-tunnistetiedot " (pr-str raportin-tunnistetiedot))
  [:div.raportti {:class (:tunniste raportin-tunnistetiedot)}
   
   ;; Raporteille mahdollista nyt antaa isompi otsikko
   (when (:nimi raportin-tunnistetiedot)
     (cond
       (and
        (= (:otsikon-koko raportin-tunnistetiedot) :iso)
        (nil? (:piilota-otsikko? raportin-tunnistetiedot)))
       [:h1 (:nimi raportin-tunnistetiedot)]

       (= (:piilota-otsikko? raportin-tunnistetiedot) true)
       [:span]

       :else
       [:h3 (:nimi raportin-tunnistetiedot)]))
   
   (keep-indexed (fn [i elementti]
                   (when elementti
                     ^{:key i}
                     [muodosta-html elementti]))
                 (mapcat (fn [sisalto]
                           (if (list? sisalto)
                             sisalto
                             [sisalto]))
                         sisalto))])

(defmethod muodosta-html :aikajana [[_ optiot rivit]]
  (aikajana/aikajana optiot rivit))

(defmethod muodosta-html :boolean [[_ {:keys [arvo]}]]
  [:div.boolean
   (kentat/vayla-checkbox {:data arvo
                           :input-id (str "harja-checkbox" (gensym))
                           :disabled? true
                           :lukutila? true ;; read only tilan ero vain disablediin: ei ole niin "harmaa". Kumpaakaan ei voi muokata
                           :arvo arvo})])

(defmethod muodosta-html :default [elementti]
  (log "HTML-raportti ei tue elementtiä: " elementti)
  nil)
