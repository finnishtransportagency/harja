(ns harja.views.hallinta.kayttajat
  "Käyttäjähallinnan näkymä"
  (:require [reagent.core :refer [atom] :as r]
            [cljs.core.async :refer [<! >! chan]]

            [harja.domain.roolit :as roolit]
            [harja.tiedot.kayttajat :as k]
            [harja.tiedot.urakat :as u]
            [harja.tiedot.navigaatio :as nav]
            [harja.ui.grid :as grid]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.yleiset :refer [ajax-loader] :as yleiset]
            [harja.ui.modal :refer [modal] :as modal]
            [harja.ui.viesti :as viesti]
            [harja.ui.komponentti :as komp]
            [harja.ui.bootstrap :as bs]

            [harja.ui.protokollat :as protokollat]
            [harja.ui.kentat :refer [tee-kentta]]

            [harja.loki :refer [log]]
            [harja.asiakas.tapahtumat :as t]
            [clojure.string :as str]
            [harja.pvm :as pvm]
            [harja.domain.roolit :refer [+rooli->kuvaus+]]
            [harja.views.kartta :as kartta])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   [harja.atom :refer [reaction<!]]))




;; Tällä hetkellä muokattava käyttäjä
(defonce valittu-kayttaja (atom nil))

(defonce haku (atom ""))

(defonce kayttajahallinnassa? (atom false))

(defonce kayttajalista (reaction<!
                        [hae? @kayttajahallinnassa?]
                        (when hae?
                          (go (second (<! (k/hae-kayttajat "" 0 500)))))))

(defonce kayttajatyyppi-rajaus (atom nil))                  ;; voi olla "L" tai "LX" jos halutaa

(def kayttajalista-rajattu
  (reaction (let [rajaus @kayttajatyyppi-rajaus
                  kaikki-kayttajat @kayttajalista]
              (if rajaus
                (filterv #(let [t (:kayttajanimi %)]
                           (or (and (= rajaus "L")
                                    (re-matches #"^L[^X].*$" t))
                               (and (= rajaus "LX")
                                    (re-matches #"^LX.*$" t))))
                         kaikki-kayttajat)
                kaikki-kayttajat))))



(defn kayttajaluettelo
  "Käyttäjälistauskomponentti"
  []
  (let [tunnus (atom "")
        haku-menossa (atom false)]
    (komp/luo
     (komp/lippu kayttajahallinnassa?)
     (fn []
       [:div.kayttajaluettelo
        (roolit/jos-rooli
         #{roolit/jarjestelmavastuuhenkilo}                 ;; +hallintayksikön vastuuhenkilö
         (let [rajaa #(reset! kayttajatyyppi-rajaus %)]
           [:span.nayta-kayttajat-valinta
            "Näytä: "
            [:input.kaikki-tunnukset {:type "radio" :on-change #(rajaa nil) :checked (nil? @kayttajatyyppi-rajaus)}]
            [:label {:on-click #(rajaa nil) :for "kaikki-tunnukset"} "kaikki"]

            [:input.vain-l-tunnukset {:type "radio" :on-change #(rajaa "L") :checked (= @kayttajatyyppi-rajaus "L")}]
            [:label {:on-click #(rajaa "L") :for "vain-l-tunnukset"} "vain L-tunnukset"]

            [:input.vain-lx-tunnukset {:type "radio" :on-change #(rajaa "LX") :checked (= @kayttajatyyppi-rajaus "LX")}]
            [:label {:on-click #(rajaa "LX") :for "vain-lx-tunnukset"} "vain LX-tunnukset"]]))
        [grid/grid
         {:otsikko       "Käyttäjät"
          :tyhja         "Ei käyttäjiä."
          :rivi-klikattu #(reset! valittu-kayttaja %)
          }

         [{:otsikko "Nimi" :hae #(str (:etunimi %) " " (:sukunimi %)) :leveys "30%"}
          {:otsikko "Livi-tunnus" :nimi :kayttajanimi :leveys "10%"}

          {:otsikko "Organisaatio" :nimi :org-nimi
           :hae     #(:nimi (:organisaatio %))
           :leveys  "25%"}

          {:otsikko "Roolit" :nimi :roolit
           :fmt     #(str/join ", " (map +rooli->kuvaus+ %))
           :leveys  "35%"}
          ]

         @kayttajalista-rajattu]

        [:form.form-inline
         [:div.form-group
          [:label {:for "tuoKayttaja"} "Tuo käyttäjä Harjaan: "]
          [:input#tuoKayttaja.form-control {:value       @tunnus
                                            :on-change   #(reset! tunnus (-> % .-target .-value))

                                            :placeholder "Livi-tunnus (LX123456)..."}]]
         [:button.nappi-toissijainen {:disabled (or @haku-menossa
                                                    (nil? (re-matches #"^\w{1,}\d*$" @tunnus)))
                                      :on-click #(do (.preventDefault %)
                                                     (reset! haku-menossa true)
                                                     (go (let [tunnus @tunnus
                                                               res (<! (k/hae-fim-kayttaja tunnus))]
                                                           (log "TULI: " res)
                                                           ;; onko valittu käyttäjä edelleen nil?
                                                           (when (nil? @valittu-kayttaja)
                                                             (if (map? res)
                                                               (reset! valittu-kayttaja res)
                                                               (if (number? res)
                                                                 (cond
                                                                   (and (<= 400 res) (> 500 res))
                                                                   (viesti/nayta! (str "Palvelinkutsu epäonnistui ("res")")
                                                                                  :danger)
                                                                   (and (<= 500 res) (> 600 res))
                                                                   (viesti/nayta! (str "FIM-palvelun kutsuminen epäonnistui ("res")")
                                                                                  :danger)
                                                                   :else (viesti/nayta! (str "Palvelinkutsu epäonnistui ("res")")
                                                                                        :warning))

                                                                 (viesti/nayta! (str "Käyttäjää " tunnus " ei löydy.")
                                                                                :warning))))
                                                           (reset! haku-menossa false))))}
          (when @haku-menossa
            [ajax-loader])
          "Tuo käyttäjä"]]]))))

(defn valitut-urakat [urakat-map]
  (into #{}
        (comp
          (filter #(not (:poistettu %)))
          (map (comp :id :urakka)))
        (vals urakat-map)))

;; Lisätty [HAR-316] vuoksi
;; Kun käyttäjää lisätään urakoitsijaksi urakkaan, on mahdollista lisätä urakka suoraan kartalta.
;; Aiemmin kartta katosi, jos urakat-gridiin lisättiin uusi rivi, tai painettiin kumoa-nappia.
;; Jotta tämä voidaan korjata, piti karttaan liittyviä arvoja kantaa mukana funktiosta funktioon,
;; joten korjaus on vähän ruma. Mutta toimii.
(defn poista-kartan-pakotus
  [k a]
  (log "poista kartan pakotus")
  ;; poista kuuntelija
  (when-let [kuuntelija @k]
    (log "poista kuuntelija")
    (kuuntelija))
  ;; poista kartan pakotus
  (swap! nav/tarvitsen-isoa-karttaa
         (fn [tk]
           (disj tk a))))

(defn valitse-kartalta [g k avain]
  (let [kuuntelija k
        avain avain]
    (r/create-class
      {:component-will-update
       (fn [this _]
         (if (not (@nav/tarvitsen-isoa-karttaa avain))
           (when-let [kk @kuuntelija]
             (log "en tarvitse karttaa, mutta minulla on kuuntelija... poistetaan!")
             (kk)
             (reset! kuuntelija nil))))

       :reagent-render
       (fn [g]
         (let [tk @nav/tarvitsen-isoa-karttaa
               kk @kuuntelija]

           [:span
            [:button.nappi-ensisijainen.pull-right
             {:on-click #(do (.preventDefault %)
                             (swap! nav/tarvitsen-isoa-karttaa
                                    (fn [tk]
                                      (if (tk avain)
                                        (disj tk avain)
                                        #{avain})))

                             (when-not (nil? @nav/valittu-urakka)
                               ;; Ei voi olla urakan kontekstissa, jos valitaan urakoita
                               (nav/valitse-urakka nil))

                             (swap! kuuntelija
                                    (fn [k]
                                      (if k
                                        (do (k) nil)
                                        (t/kuuntele! :urakka-klikattu
                                                     (fn [urakka]
                                                       (let [urakat (valitut-urakat (grid/hae-muokkaustila g))]
                                                         (log "jo valitut urakat: " urakat ", nyt ollaan valitsemassa: " (dissoc urakka :alue))
                                                         (when-not (urakat (:id urakka))
                                                           (grid/lisaa-rivi! g {:urakka urakka
                                                                                :luotu  (pvm/nyt)})))))))))}
             (if (nil? @kuuntelija)
               "Valitse kartalta"
               "Piilota kartta")]]))})))

(defn urakkalista [virheet urakat-atom organisaatio kartan-kuuntelija kartan-avain]
  [:span
   [grid/muokkaus-grid
    {:otsikko        "Urakat"
     :tyhja          "Ei liitettyjä urakoita."
     :muokkaa-footer (fn [g]
                       [:div.urakkalista-napit
                        (when (#{:hallintayksikko :urakoitsija} (:tyyppi organisaatio))
                          [:button.nappi-toissijainen {:on-click #(do (.preventDefault %)
                                                                      (go (let [res (<! (k/hae-organisaation-urakat (:id organisaatio)))
                                                                                urakat (valitut-urakat @urakat-atom)]
                                                                            (doseq [u res]
                                                                              (when-not (urakat (:id u))
                                                                                (swap! urakat-atom assoc (:id u) {:urakka u :luotu (pvm/nyt)}))))))}
                           (str "Lisää kaikki " (case (:tyyppi organisaatio)
                                                  :hallintayksikko "hallintayksikön"
                                                  :urakoitsija "urakoitsijan") " urakat")])
                        [valitse-kartalta g kartan-kuuntelija kartan-avain]])

     :uusi-rivi      #(assoc % :luotu (pvm/nyt))
     :muutos         (fn [g]
                       (log "VIRHEITÄ " (grid/hae-virheet g))
                       (reset! virheet (count (grid/hae-virheet g)))
                       (log "gridi muuttui: " g))
     }
    [{:otsikko "Liitetty urakka" :leveys "50%" :nimi :urakka
      :tyyppi  :haku
      :nayta   :nimi :fmt :nimi
      :lahde   (reify protokollat/Haku
                 ;; Tehdään oma haku urakkahaun pohjalta, joka ei näytä jo valittuja urakoita
                 (hae [_ teksti]
                   (let [ch (chan)]
                     (go (let [res (<! (protokollat/hae u/urakka-haku teksti))
                               urakat (valitut-urakat @urakat-atom)]
                           (log "JO OLEMASSA OLEVAT " urakat)
                           (>! ch (into []
                                        (filter #(not (urakat (:id %))))
                                        res))))
                     ch)))
      :validoi [[:ei-tyhja "Valitse urakka"]]}
     {:otsikko "Hallintayksikkö" :leveys "30%" :muokattava? (constantly false) :nimi :hal-nimi :hae (comp :nimi :hallintayksikko :urakka) :tyyppi :string}
     {:otsikko "Lisätty" :leveys "20%" :nimi :luotu :tyyppi :string
      :fmt     pvm/pvm-opt :muokattava? (constantly false)}]

    urakat-atom]])

(defn organisaatiovalinta
  "Komponentti organisaation valitsemiseksi."
  [organisaatio]
  (log "ORG: " organisaatio)
  [tee-kentta {:tyyppi      :haku
               :pituus      50
               :placeholder "Hae hallintayksikkö / urakoitsija..."
               :nayta       :nimi
               :lahde       k/organisaatio-haku}
   organisaatio])


;; kartan-kuuntelija, kartan-avain lisätty [HAR-316] vuoksi
;; Kun käyttäjää lisätään urakoitsijaksi urakkaan, on mahdollista lisätä urakka suoraan kartalta.
;; Aiemmin kartta katosi, jos urakat-gridiin lisättiin uusi rivi, tai painettiin kumoa-nappia.
;; Jotta tämä voidaan korjata, piti karttaan liittyviä arvoja kantaa mukana funktiosta funktioon,
;; joten korjaus on vähän ruma. Mutta toimii.
(defn kayttajatiedot [k]
  (let [kartan-kuuntelija (atom nil)
        kartan-avain (gensym "kayttajat")
        organisaatio (atom (:organisaatio k))
        tyyppi (reaction (case (:tyyppi @organisaatio)
                           (:hallintayksikko :liikennevirasto) :tilaaja
                           :urakoitsija :urakoitsija
                           nil))

        roolit (atom (into #{} (:roolit k)))
        toggle-rooli! (fn [r]
                        (swap! roolit (fn [roolit]
                                        (if (roolit r)
                                          (disj roolit r)
                                          (conj roolit r)))))
        roolivalinta (fn [rooli & sisalto]
                       (let [valittu (@roolit rooli)]
                         [:div.rooli
                          [:div.roolivalinta
                           [:input {:type      "checkbox" :checked valittu
                                    :on-change #(toggle-rooli! rooli)
                                    :name      rooli}]
                           " "
                           [:label {:for      rooli
                                    :on-click #(toggle-rooli! rooli)} (+rooli->kuvaus+ rooli)]]
                          [:div.rooli-lisavalinnat
                           ;; Piilotetaan tämä displayllä, ei poisteta kokonaan, koska halutaan säilyttää
                           ;; tila jos käyttäjä klikkaa roolin pois päältä ja takaisin.
                           {:style {:display (when-not (and valittu (not (empty? sisalto)))
                                               "none")}}
                           sisalto]]))
        tiedot (atom {})

        ;; tekee urakkaroolilistasta {<idx> <urakkarooli>} array-mapin, muokkausgridiä varten
        urakat-muokattava #(into {}
                                 (map (fn [urakkarooli]
                                        [(:id (:urakka urakkarooli)) urakkarooli]) %))

        urakanvalvoja-urakat (atom (array-map))
        tilaajan-laadunvalvontakonsultti-urakat (atom (array-map))
        urakan-vastuuhenkilo-urakat (atom (array-map))
        urakoitsijan-kayttaja-urakat (atom (array-map))
        urakoitsijan-laatuvastaava-urakat (atom (array-map))

        ;; tekee muokattavasta urakkalistasta tallenusmuotoisen
        urakat-tallennus (fn [muokattavat rooli]
                           (map (fn [urakkarooli]
                                  ;; poistetaan urakka ja hallintayksikkö ja lähetetään vain id:t
                                  (assoc urakkarooli
                                    :urakka {:id (get-in urakkarooli [:urakka :id])}
                                    ;;:hallintayksikko {:id (get-in urakkarooli [:hallintayksikko :id])}
                                    :rooli rooli))
                                (vals muokattavat)))

        virheet (atom {})                                   ;; urakkalistan nimi => virheiden lkm

        tallennus-menossa (atom false)
        tallenna! (fn []
                    (reset! tallennus-menossa true)
                    (log "TALLENNETAAN KÄYTTÄJÄÄ")
                    (go
                      (let [uudet-tiedot
                            (<! (k/tallenna-kayttajan-tiedot!
                                  k
                                  @organisaatio
                                  {:roolit @roolit
                                   :urakka-roolit
                                           (into []
                                                 (concat
                                                   (urakat-tallennus @urakanvalvoja-urakat "urakanvalvoja")
                                                   (urakat-tallennus @tilaajan-laadunvalvontakonsultti-urakat "tilaajan laadunvalvontakonsultti")
                                                   (urakat-tallennus @urakan-vastuuhenkilo-urakat "urakoitsijan urakan vastuuhenkilo")
                                                   (urakat-tallennus @urakoitsijan-kayttaja-urakat "urakoitsijan kayttaja")
                                                   (urakat-tallennus @urakoitsijan-laatuvastaava-urakat "urakoitsijan laatuvastaava")))
                                   }))]
                        (reset! valittu-kayttaja nil)
                        (swap! kayttajalista
                               (fn [kl]
                                 (if (some #(= (:id %) (:id uudet-tiedot)) kl)
                                   ;; käyttäjä on jo, päivitetään se
                                   (mapv #(if (= (:id %) (:id k))
                                           ;; päivitetään käyttäjän roolit näkymään
                                           (assoc % :roolit (:roolit uudet-tiedot))
                                           %) kl)
                                   ;; uusi käyttäjä lisätään listaan
                                   (conj kl uudet-tiedot))))
                        (viesti/nayta! "Käyttäjä tallennettu." :success)
                        (reset! tallennus-menossa false))))

        poista! (fn []
                  (go
                    (if (<! (k/poista-kayttaja! (:id k)))
                      (do (log "poistettiin")
                          (reset! valittu-kayttaja nil)
                          (swap! kayttajalista
                                 (fn [kl]
                                   (filterv #(not= (:id %) (:id k)) kl)))
                          (viesti/nayta! [:span "Käyttäjän " [:b (:etunimi k) " " (:sukunimi k)] " käyttöoikeus poistettu."]))
                      (viesti/nayta! "Käyttöoikeuden poisto epäonnistui!" :warning))))
        ]

    (go (reset! tiedot (<! (k/hae-kayttajan-tiedot (:id k)))))
    (run! (let [tiedot @tiedot]

            (let [urakka-roolit (group-by :rooli (:urakka-roolit tiedot))]
              (log "URAKKA ROOLIT : " urakka-roolit)
              (reset! urakanvalvoja-urakat
                      (urakat-muokattava (or (get urakka-roolit "urakanvalvoja") [])))
              (log "urva: " (urakat-muokattava (or (get urakka-roolit "urakanvalvoja") [])))
              (reset! tilaajan-laadunvalvontakonsultti-urakat
                      (urakat-muokattava (or (get urakka-roolit "tilaajan laadunvalvontakonsultti") [])))
              (reset! urakan-vastuuhenkilo-urakat
                      (urakat-muokattava (or (get urakka-roolit "urakoitsijan urakan vastuuhenkilo") [])))
              (reset! urakoitsijan-kayttaja-urakat
                      (urakat-muokattava (or (get urakka-roolit "urakoitsijan kayttaja") [])))
              (reset! urakoitsijan-laatuvastaava-urakat
                      (urakat-muokattava (or (get urakka-roolit "urakoitsijan laatuvastaava") [])))

              )))


    (r/create-class
      {:component-will-unmount
       (fn []
         (poista-kartan-pakotus kartan-kuuntelija kartan-avain))

       :reagent-render
       (fn [k]
         [:div.kayttajatiedot
          [:button.nappi-toissijainen {:on-click #(reset! valittu-kayttaja nil)}
           [:span.livicon-chevron-left " Takaisin käyttäjäluetteloon"]]

          [:h3 "Muokkaa käyttäjää " (:etunimi k) " " (:sukunimi k)]

          [bs/panel
           {} "Perustiedot"
           [yleiset/tietoja
            {}
            "Nimi:" [:span.nimi (:etunimi k) " " (:sukunimi k)]
            "Sähköposti:" [:span.sahkoposti (:sahkoposti k)]
            "Puhelinnumero:" [:span.puhelin (:puhelin k)]
            (case (:tyyppi @organisaatio)
              :liikennevirasto ""
              :hallintayksikko "Hallintayksikkö:"
              :urakoitsija "Urakoitsija:"
              nil "Valitse organisaatio:") (if-let [org @organisaatio]
                                             (:nimi @organisaatio)
                                             [organisaatiovalinta organisaatio])
            ]]

          (let [tyyppi @tyyppi]
            [:form.form-horizontal
             [kartta/kartan-paikka]
             ;; Valitaan käyttäjän tyyppi
             [:div.form-group
              [:label.col-sm-2.control-label {:for "kayttajatyyppi"}
               "Käyttäjätyyppi"]
              [:div.col-sm-10
               (if tyyppi
                 [:span (case tyyppi
                          :tilaaja "Tilaaja"
                          :urakoitsija "Urakoitsija")]
                 "Valitse organisaatio")]]

             ;; Käyttäjän roolit
             [:div.form-group
              [:label.col-sm-2.control-label
               "Roolit:"]
              [:div.col-sm-10.roolit

               ;; kartan-kuuntelija, kartan-avain lisätty [HAR-316] vuoksi
               ;; Kun käyttäjää lisätään urakoitsijaksi urakkaan, on mahdollista lisätä urakka suoraan kartalta.
               ;; Aiemmin kartta katosi, jos urakat-gridiin lisättiin uusi rivi, tai painettiin kumoa-nappia.
               ;; Jotta tämä voidaan korjata, piti karttaan liittyviä arvoja kantaa mukana funktiosta funktioon,
               ;; joten korjaus on vähän ruma. Mutta toimii.
               (cond
                 (= tyyppi :tilaaja)
                 [:span
                  [roolivalinta "jarjestelmavastuuhenkilo"]
                  (when (= :hallintayksikko (:tyyppi @organisaatio))
                    [roolivalinta "hallintayksikon vastuuhenkilo"])
                  [roolivalinta "tilaajan kayttaja"]
                  [roolivalinta "urakanvalvoja"
                   ^{:key "urakat"}
                   [urakkalista (r/wrap (@virheet "urakanvalvoja")
                                        #(swap! virheet assoc "urakanvalvoja" %))
                    urakanvalvoja-urakat @organisaatio kartan-kuuntelija kartan-avain]]

                  [roolivalinta "tilaajan asiantuntija"]
                  [roolivalinta "tilaajan laadunvalvontakonsultti"
                   ^{:key "urakat"}
                   [urakkalista (r/wrap (@virheet "tilaajan laadunvalvontakonsultti")
                                        #(swap! virheet assoc "tilaajan laadunvalvontakonsultti" %))
                    tilaajan-laadunvalvontakonsultti-urakat @organisaatio kartan-kuuntelija kartan-avain]]]

                 (= tyyppi :urakoitsija)
                 ;; urakoitsijan roolit
                 [:span
                  [roolivalinta "urakoitsijan paakayttaja"]
                  [roolivalinta "urakoitsijan urakan vastuuhenkilo"
                   ^{:key "urakat"}
                   [urakkalista (r/wrap (@virheet "urakoitsijan urakan vastuuhenkilo")
                                        #(swap! virheet assoc "urakoitsijan urakan vastuuhenkilo" %))
                    urakan-vastuuhenkilo-urakat @organisaatio kartan-kuuntelija kartan-avain]]

                  [roolivalinta "urakoitsijan kayttaja"
                   ^{:key "urakat"}
                   [urakkalista (r/wrap (@virheet "urakoitsijan kayttaja")
                                        #(swap! virheet assoc "urakoitsijan kayttaja" %))
                    urakoitsijan-kayttaja-urakat @organisaatio kartan-kuuntelija kartan-avain]]
                  [roolivalinta "urakoitsijan laatuvastaava"
                   ^{:key "urakat"}
                   [urakkalista (r/wrap (@virheet "urakoitsijan laatuvastaava")
                                        #(swap! virheet assoc "urakoitsijan laatuvastaava" %))
                    urakoitsijan-laatuvastaava-urakat @organisaatio kartan-kuuntelija kartan-avain]]]

                 :default "Valitse organisaatio")]]

             [:div.form-group
              [:label.col-sm-2.control-label
               "Toiminnot:"]
              [:div.col-sm-10.toiminnot
               (let [virheita (some pos? (map second @virheet))]
                 [:button.nappi-ensisijainen {:disabled (or (nil? tyyppi) virheita)
                                              :on-click #(do (.preventDefault %)
                                                             (tallenna!))}
                  (if @tallennus-menossa
                    [ajax-loader]
                    (ikonit/tallenna)) " Tallenna"])
               [:span.pull-right
                [:button.nappi-kielteinen {:on-click
                                           (fn [e]
                                             (do (.preventDefault e)
                                                 (if (nil? (:id k))
                                                   (reset! valittu-kayttaja nil)
                                                   (modal/nayta! {:otsikko "Poistetaanko käyttöoikeus?"
                                                                  :footer  [:span
                                                                            [:button.nappi-toissijainen {:type     "button"
                                                                                                         :on-click #(do (.preventDefault %)
                                                                                                                        (modal/piilota!))}
                                                                             "Peruuta"]
                                                                            [:button.nappi-kielteinen {:type     "button"
                                                                                                       :on-click #(do (.preventDefault %)
                                                                                                                      (modal/piilota!)
                                                                                                                      (poista!))}
                                                                             "Poista käyttöoikeus"]
                                                                            ]}
                                                                 [:div "Haluatko varmasti poistaa käyttäjän "
                                                                  [:b (:etunimi k) " " (:sukunimi k)] " Harja-käyttöoikeuden?"]))))}
                 (ikonit/ban) (if (nil? (:id k)) " Peruuta" " Poista käyttöoikeus")]]]]])])})))

(defn kayttajat
  "Käyttäjähallinnan pääkomponentti"
  []
  (if-let [vk @valittu-kayttaja]
    [kayttajatiedot vk]
    [kayttajaluettelo]))
