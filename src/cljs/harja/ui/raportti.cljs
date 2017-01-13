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
            [harja.fmt :as fmt]))

(defmulti muodosta-html
  "Muodostaa Reagent komponentin annetulle raporttielementille."
  (fn [elementti]
    (if (raportti-domain/raporttielementti? elementti)
      (first elementti)
      :vain-arvo)))

(defmethod muodosta-html :vain-arvo [arvo] arvo)

(defmethod muodosta-html :liitteet [[_ liitteet]]
  (liitteet/liitteet-numeroina liitteet))

(defmethod muodosta-html :arvo-ja-osuus [[_ {:keys [arvo osuus fmt]}]]
  [:span.arvo-ja-osuus
   [:span.arvo (if fmt (fmt arvo) arvo)]
   [:span " "]
   [:span.osuus (str "(" osuus "%)")]])

(defmethod muodosta-html :varillinen-teksti
  ;; :varillinen-teksti elementtiä voidaan käyttää mm. virheiden näyttämiseen. Pyritään aina käyttämään
  ;; ennaltamääriteltyjä tyylejä, mutta jos on erikoistapaus missä halutaan käyttää itsemääriteltyä väriä,
  ;; voidaan käyttää avainta :itsepaisesti-maaritelty-oma-vari
  [[_ {:keys [arvo tyyli itsepaisesti-maaritelty-oma-vari fmt]}]]
  [:span.varillinen-teksti
   [:span.arvo {:style {:color (or itsepaisesti-maaritelty-oma-vari (raportti-domain/virhetyylit tyyli) "rgb(25,25,25)")}}
    (if fmt (fmt arvo) arvo)]])

(defmethod muodosta-html :taulukko [[_ {:keys [otsikko viimeinen-rivi-yhteenveto?
                                               rivi-ennen
                                               tyhja
                                               korosta-rivit korostustyyli
                                               oikealle-tasattavat-kentat]}
                                     sarakkeet data]]
  (let [oikealle-tasattavat-kentat (or oikealle-tasattavat-kentat #{})
        formatter (fn [{fmt :fmt}]
                    (case fmt
                      :numero #(raportti-domain/yrita fmt/desimaaliluku-opt % 2 true)
                      :prosentti #(raportti-domain/yrita fmt/prosentti-opt % 1)
                      :raha #(raportti-domain/yrita fmt/euro-opt % )
                      :pvm #(raportti-domain/yrita fmt/pvm-opt %)
                      str))]
    [grid/grid {:otsikko            (or otsikko "")
                :tunniste           (fn [rivi] (str "raportti_rivi_"
                                                    (or (::rivin-indeksi rivi)
                                                        (hash rivi))))
                :rivi-ennen rivi-ennen
                :piilota-toiminnot? true}
     (into []
           (map-indexed (fn [i sarake]
                          (let [raporttielementteja? (raportti-domain/sarakkeessa-raporttielementteja? i data)
                                format-fn (formatter sarake)]
                            (merge
                             {:hae                #(get % i)
                              :leveys             (:leveys sarake)
                              :otsikko            (:otsikko sarake)
                              :reunus             (:reunus sarake)
                              :pakota-rivitys?    (:pakota-rivitys? sarake)
                              :otsikkorivi-luokka (str (:otsikkorivi-luokka sarake)
                                                       (case (:tasaa-otsikko sarake)
                                                         :keskita " grid-header-keskita"
                                                         :oikea " grid-header-oikea"
                                                         ""))
                              :nimi               (str "sarake" i)
                              :fmt                format-fn
                              ;; Valtaosa raporttien sarakkeista on puhdasta tekstiä, poikkeukset komponentteja
                              :tyyppi             (if raporttielementteja?
                                                    :komponentti
                                                    :string)
                              :tasaa              (if (oikealle-tasattavat-kentat i)
                                                    :oikea
                                                    (:tasaa sarake))}
                             (when raporttielementteja?
                               {:komponentti (fn [rivi]
                                               (let [elementti (get rivi i)]
                                                 (muodosta-html (raportti-domain/raporttielementti-formatterilla elementti format-fn))))}))))
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
                                      lihavoi? (:lihavoi? optiot)
                                      korosta? (:korosta? optiot)
                                      mappina (assoc
                                                (zipmap (range (count sarakkeet))
                                                        rivi)
                                                ::rivin-indeksi index)]
                                  (cond-> mappina
                                          (and viimeinen-rivi-yhteenveto?
                                               (= viimeinen-rivi rivi))
                                          (assoc :yhteenveto true)

                                          (or korosta? (when korosta-rivit (korosta-rivit index)))
                                          (assoc :korosta true)

                                          lihavoi?
                                          (assoc :lihavoi true))))))
               data)))]))


(defmethod muodosta-html :otsikko [[_ teksti]]
  [:h3 teksti])

(defmethod muodosta-html :otsikko-kuin-pylvaissa [[_ teksti]]
  [:h3 teksti])

(defmethod muodosta-html :teksti [[_ teksti {:keys [vari]}]]
  [:p {:style {:color (when vari vari)}} teksti])

(defmethod muodosta-html :varoitusteksti [[_ teksti]]
  (muodosta-html [:teksti teksti {:vari "#dd0000"}]))

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
   (when (:nimi raportin-tunnistetiedot)
     [:h3 (:nimi raportin-tunnistetiedot)])
   (keep-indexed (fn [i elementti]
                   (when elementti
                     ^{:key i}
                     [muodosta-html elementti]))
                 (mapcat (fn [sisalto]
                           (if (list? sisalto)
                             sisalto
                             [sisalto]))
                         sisalto))])

(defmethod muodosta-html :default [elementti]
  (log "HTML-raportti ei tue elementtiä: " elementti)
  nil)
