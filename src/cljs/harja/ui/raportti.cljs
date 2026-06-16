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
  (:require [harja.fmt :as fmt]
            [harja.pvm :as pvm]
            [harja.ui.dom :as dom]
            [harja.ui.grid :as grid]
            [harja.loki :refer [log]]
            [harja.tiedot.urakka :as u]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.kentat :as kentat]
            [harja.visualisointi :as vis]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.liitteet :as liitteet]
            [harja.ui.aikajana :as aikajana]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.domain.raportointi :as raportti-domain]
            [harja.tiedot.urakka.siirtymat :as siirtymat]))

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

(defmethod muodosta-html :arvo-yksikko-ja-osuus [[_ {:keys [arvo osuus fmt yksikko]}]]
  [:span.arvo-ja-osuus
   [:span.arvo (if fmt (fmt arvo) arvo)]
   [:span.yksikko (str "\u00A0" yksikko)]
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

(defmethod muodosta-html :saa-ikoni [[_ {:keys [olomuoto havaintoaika maara]}]]
  ;; Generoidaan sää ikoni olomuotoon ja havaintoaikaan nähden
  (if olomuoto
    [ikonit/generoi-saa-ikoni (int olomuoto) (int maara) havaintoaika]
    [:span raportti-domain/placeholder-ei-tietoja]))

(defmethod muodosta-html :varillinen-teksti
  ;; :varillinen-teksti elementtiä voidaan käyttää mm. virheiden näyttämiseen. Pyritään aina käyttämään
  ;; ennaltamääriteltyjä tyylejä, mutta jos on erikoistapaus missä halutaan käyttää itsemääriteltyä väriä,
  ;; voidaan käyttää avainta :itsepaisesti-maaritelty-oma-vari
  [[_ {:keys [arvo tyyli itsepaisesti-maaritelty-oma-vari fmt lihavoi? kustomi-tyyli teksti-alle]}]]

  (let [urakka-id (-> @tila/yleiset :urakka :id)
        lihavoi (when lihavoi? {:font-weight "bold"})
        hy (-> @tila/yleiset :urakka :elinvoimakeskus :id)
        valittu-alkuvuosi (some->> @u/valittu-hoitokausi first pvm/vuosi)]

    [:span.varillinen-teksti
     [:span.arvo {:class kustomi-tyyli
                  :style (merge lihavoi
                           {:color (or itsepaisesti-maaritelty-oma-vari (raportti-domain/virhetyylit tyyli) "rgb(25,25,25)")})}
      (if fmt (fmt arvo) arvo)
      (when teksti-alle [:<> [:br]
                         (siirtymat/tee-siirrin-valikatselmukseen teksti-alle urakka-id hy valittu-alkuvuosi)])]]))

(defmethod muodosta-html :osittain-boldattu-teksti
  ;; Joihinkin teksteihin halutaan osittain boldattu teksti. Tämä elementti mahdollistaa sen.
  [[_ {:keys [boldattu-teksti teksti] :as tiedot}]]
  [:span.osittain-boldattu-teksti {:style {:display "block"}}
   [:span.bold boldattu-teksti]
   [:span teksti]])

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
    :numero-opt #(raportti-domain/yrita fmt/desimaaliluku-opt % 0 2 true)
    :numero-3desim #(fmt/pyorista-ehka-kolmeen %)
    :prosentti #(raportti-domain/yrita fmt/prosentti-opt % 1)
    :prosentti-0desim #(raportti-domain/yrita fmt/prosentti-opt % 0)
    :raha #(raportti-domain/yrita fmt/euro-opt %)
    :pvm #(raportti-domain/yrita fmt/pvm-opt %)
    str))

(defn grid [otsikko gridin-luokka
            viimeinen-rivi-yhteenveto?
            rivi-ennen piilota-border?
            raportin-tunniste tyhja
            korosta-rivit korostustyyli
            oikealle-tasattavat-kentat vetolaatikot
            sivuttain-rullattava? ensimmainen-sarake-sticky?
            esta-tiivis-grid? avattavat-rivit
            ei-footer-muokkauspaneelia?
            sarakkeet data]
  (let [oikealle-tasattavat-kentat (or oikealle-tasattavat-kentat #{})]
    [grid/grid {:otsikko (or otsikko "")
                :otsikon-luokka "raportti-otsikko"
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
                :raportin-tunniste raportin-tunniste
                :ei-footer-muokkauspaneelia? ei-footer-muokkauspaneelia?
                :gridin-luokka gridin-luokka}
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
                                  (when (:huomio? (and (vector? arvo) (second arvo)))
                                    " solu-huomio ")
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
                             (and (raportti-domain/numero-fmt? (:fmt sarake)) (not= :vasen (:tasaa sarake))))
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
                                (and
                                  (= viimeinen-rivi rivi)
                                  viimeinen-rivi-yhteenveto?)
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

(defmethod muodosta-html :taulukko [[_ {:keys [otsikko
                                               gridin-luokka
                                               viimeinen-rivi-yhteenveto?
                                               rivi-ennen
                                               piilota-border?
                                               raportin-tunniste
                                               tyhja
                                               korosta-rivit korostustyyli
                                               oikealle-tasattavat-kentat vetolaatikot esta-tiivis-grid?
                                               avattavat-rivit sivuttain-rullattava? ensimmainen-sarake-sticky?
                                               ei-footer-muokkauspaneelia?]}
                                     sarakkeet data]]
  [grid otsikko gridin-luokka
   viimeinen-rivi-yhteenveto?
   rivi-ennen piilota-border?
   raportin-tunniste tyhja
   korosta-rivit korostustyyli
   oikealle-tasattavat-kentat vetolaatikot
   esta-tiivis-grid? avattavat-rivit
   sivuttain-rullattava? ensimmainen-sarake-sticky?
   ei-footer-muokkauspaneelia?
   sarakkeet data])

(defmethod muodosta-html :otsikko-title [[_ teksti]]
  [:h1.raportti-otsikko teksti])

(defmethod muodosta-html :otsikko-heading [[_ teksti tyyli]]
  [:h2.raportti-otsikko {:style (merge {:font-size "20px"} tyyli)} teksti])

(defmethod muodosta-html :info-laatikko [[_ ensisijainen-teksti toissijainen-teksti leveys]]
  (yleiset/info-laatikko :vahva-ilmoitus ensisijainen-teksti toissijainen-teksti leveys {:ikoni-fn #(ikonit/harja-icon-status-alert) :luokka "pull-right"}))

(defmethod muodosta-html :otsikko-heading-small [[_ teksti]]
  [:h1.raportti-otsikko {:style {:font-size "12px"}} teksti])

(defmethod muodosta-html :otsikko [[_ teksti]]
  [:h3.raportti-otsikko teksti])

(defmethod muodosta-html :jakaja [ei-valitysta]
  (if ei-valitysta
    [:hr]
    [:hr {:style {:margin-top "30px"
                  :margin-bottom "30px"}}]))

(defmethod muodosta-html :tyhja-rivi [_]
  [:div {:style {:height "0.75rem"}}])

(defmethod muodosta-html :otsikko-kuin-pylvaissa [[_ teksti]]
  [:h3.raportti-otsikko teksti])

(defmethod muodosta-html :teksti [[_ teksti {:keys [vari infopallura rivita? alamarginaali leveysprosentti]}]]
  [:div {:style (merge
                  {:color (when vari vari)}
                  (when leveysprosentti {:width (str leveysprosentti "%")})
                  (when rivita? {:white-space "pre-line"})
                  (when alamarginaali {:margin-bottom alamarginaali}))}
   teksti
   (when infopallura (muodosta-html [:infopallura infopallura]))])

(defmethod muodosta-html :teksti-paksu [[_ teksti {:keys [vari infopallura leveysprosentti]}]]
  [:div {:style (merge
                  {:font-weight 700
                   :color (when vari vari)}
                  (when leveysprosentti {:width (str leveysprosentti "%")}))} teksti
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
     [vis/bars {:width w
                :height h
                :format-amount (or fmt str)
                :hide-value? piilota-arvo?
                :legend legend}
      pylvaat]]))

(defmethod muodosta-html :piirakka [[_ {:keys [otsikko]} data]]
  [:div.pylvaat
   [:h3 otsikko]
   [vis/pie
    {:width 230 :height 150 :radius 60 :show-text :percent :show-legend true}
    data]])

(defmethod muodosta-html :sininen-laatikko [[_ {:keys [otsikko layout nayta-hr?]
                                                :or {nayta-hr? true}} data]]
  (case layout
    ;; Data sarakkeina otsikoineen, esimerkiksi laskutusyhteenvedossa
    :sarakkeet
    (into
      [:div.sininen-laatikko
       [:h3 otsikko]]
      [(into
         [:div.sininen-laatikko-sarakkeet]
         (map-indexed
           (fn [i rivi]
             ^{:key (str "sininen-laatikko-sarake-" i)}
             [:div.sininen-laatikko-sarake
              [:div.sininen-laatikko-label.caption (:avain rivi)]
              [:h1 {:class (when (:korosta? rivi) "vahvistamaton")}
               (if (= :raha (:fmt rivi))
                 (fmt/euro-opt (:arvo rivi))
                 (:arvo rivi))]])
           data))])

    ;; Data riveinä, viimeinen rivi yhteenveto
    (let [viimeinen-idx (dec (count data))]
      (into
        [:div.sininen-laatikko [:h3 otsikko]]
        (map-indexed
          (fn [i rivi]
            ^{:key (str "sininen-laatikko-rivi-" i)}
            [:div
             ;; Viimeiselle riville jakaja
             (when (and nayta-hr? (= i viimeinen-idx)) [:hr])

             ;; Kontentti
             [:div.flex-row
              (cond-> {} (:lihavoi? rivi) (assoc :style {:font-weight "bold"}))
              [:div (:avain rivi)]
              [:div.tasaa-oikealle
               (if (= :raha (:fmt rivi)) (fmt/euro-opt (:arvo rivi)) (:arvo rivi))]]])
          data)))))

(defmethod muodosta-html :laskutusyhteenveto-otsikko [[_ teksti]]
  [:h2 {:style {:font-size "16px"}} teksti])

(defmethod muodosta-html :display-flex [[_ & data]]
  (into
    [:div.display-flex.display-container]
    (map-indexed
      (fn [i d]
        ^{:key (str "display-flex-" i)}
        [muodosta-html d])
      data)))

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

       (= (:otsikon-koko raportin-tunnistetiedot) :keskikoko)
       [:h1 {:style {:font-size "20px"}} (:nimi raportin-tunnistetiedot)]

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
