(ns harja.views.urakka.yleiset
  "Urakan 'Yleiset' välilehti: perustiedot ja yhteyshenkilöt"
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.bootstrap :as bs]
            [harja.ui.grid :as grid]
            [harja.ui.yleiset :as yleiset]
            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.urakka :as urakka]
            [harja.domain.urakka :as u]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.hallinta.indeksit :as indeksit]
            [harja.tiedot.urakka.yleiset :as tiedot]
            [harja.tiedot.urakka.sopimustiedot :as sopimus]
            [harja.tiedot.navigaatio :as navigaatio]
            [harja.tiedot.urakka.yhatuonti :as yhatiedot]
            [harja.views.urakka.yhatuonti :as yha]
            [harja.loki :refer [log tarkkaile!]]
            [harja.pvm :as pvm]

            [cljs.core.async :refer [<!]]
            [clojure.string :as str]
            [cljs-time.core :as t]
            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.modal :as modal]
            [harja.domain.oikeudet :as oikeudet]
            [harja.ui.komponentti :as komp]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.fmt :as fmt]
            [harja.ui.ikonit :as ikonit]
            [reagent.core :as r]
            [harja.ui.viesti :as viesti]
            [harja.domain.roolit :as roolit]
            [harja.domain.vesivaylat.alus :as alus]
            [harja.ui.napit :as napit]
            [harja.tiedot.urakat :as urakat]
            [harja.tiedot.urakka.urakan-tyotunnit :as urakan-tyotunnit]
            [harja.ui.lomake :as lomake]
            [harja.views.urakka.paallystys-indeksit :as paallystys-indeksit]
            [harja.views.urakka.yleiset.paivystajat :as paivystajat]
            [harja.domain.urakka :as u-domain]
            [harja.domain.urakka :as urakka-domain]
            [taoensso.timbre :as log])
  (:require-macros [cljs.core.async.macros :refer [go]]))

;; hallintayksikkö myös
;; organisaatio = valinta siitä mitä on tietokannassa
;; sampoid

(defn tallenna-yhteyshenkilot [ur yhteyshenkilot uudet-yhteyshenkilot]
  (go (let [tallennettavat
            (into []
                  ;; Kaikki tiedon mankelointi ennen lähetystä tähän
                  (comp (filter #(not (:poistettu %)))
                        (map #(if-let [nimi (:nimi %)]
                                (let [[_ etu suku] (re-matches #"^ *([^ ]+)( *.*?) *$" nimi)]
                                  (assoc %
                                    :etunimi (str/trim etu)
                                    :sukunimi (str/trim suku)))
                                %)))
                  uudet-yhteyshenkilot)
            poistettavat
            (into []
                  (keep #(when (and (:poistettu %)
                                    (> (:id %) 0))
                           (:id %)))
                  uudet-yhteyshenkilot)
            res (<! (tiedot/tallenna-urakan-yhteyshenkilot (:id ur) tallennettavat poistettavat))]
        (reset! yhteyshenkilot res)
        true)))



(defn tallenna-sopimustyyppi [ur uusi-sopimustyyppi]
  (go (let [res (<! (sopimus/tallenna-sopimustyyppi (:id ur) uusi-sopimustyyppi))]
        (if-not (k/virhe? res)
          (nav/paivita-urakan-tiedot! (:id ur) assoc :sopimustyyppi res)
          true))))

(defn vahvista-urakkatyypin-vaihtaminen [ur uusi-urakkatyyppi]
  (when-not (= uusi-urakkatyyppi (:tyyppi ur))
    (let [vaihda-urakkatyyppi (fn []
                                (go (let [res (<! (urakka/vaihda-urakkatyyppi
                                                    (:id ur)
                                                    (name uusi-urakkatyyppi)))]
                                      (if-not (k/virhe? res)
                                        (nav/paivita-urakan-tiedot! (:id ur) assoc :tyyppi res)
                                        true))))]
      (modal/nayta!
        {:otsikko "Vaihdetaanko urakkatyyppi?"
         :footer [:span
                  [:button.nappi-toissijainen {:type "button"
                                               :on-click #(do (.preventDefault %)
                                                              (modal/piilota!))}
                   "Peruuta"]
                  [:button.nappi-myonteinen {:type "button"
                                             :on-click #(do (.preventDefault %)
                                                            (modal/piilota!)
                                                            (vaihda-urakkatyyppi))}
                   "Vaihda"]]}
        [:div
         [:p (str "Haluatko varmasti vaihtaa " (navigaatio/nayta-urakkatyyppi (:tyyppi ur))
                  "-tyyppisen urakan ")
          [:strong (str (navigaatio/nayta-urakkatyyppi uusi-urakkatyyppi) "-tyyppiseksi")] "?"]]))))

(defn urakkaan-liitetyt-kayttajat [kayttajat]
  [grid/grid
   {:otsikko "Käyttövaltuushallinnassa urakkaan liitetyt käyttäjät"
    :tunniste :kayttajatunnus
    :tyhja (cond
             (nil? kayttajat)
             [yleiset/ajax-loader
              "Haetaan käyttövaltuushallinnassa urakkaan liitettyjä käyttäjiä"]

             (k/virhe? kayttajat)
             "Virhe haettaessa käyttäjiä käyttövaltuushallinnan FIM-palvelusta."

             :default
             "Ei urakkaan liitettyjä käyttäjiä.")}

   [{:otsikko "Rooli" :nimi :roolit :fmt #(str/join ", " %) :tyyppi :string :leveys "15%"}
    {:otsikko "Organisaatio" :nimi :organisaatio :tyyppi :string :leveys "15%"}
    {:otsikko "Nimi" :nimi :nimi :hae #(str (:etunimi %) " " (:sukunimi %)) :tyyppi :string
     :leveys "25%"}
    {:otsikko "Puhelin" :nimi :puhelin :tyyppi :string :leveys "20%"}
    {:otsikko "Sähköposti" :nimi :sahkoposti :tyyppi :string :leveys "25%"}]
   (if (k/virhe? kayttajat)
     []
     kayttajat)])

(defn urakan-tyotuntilista [tyotunnit tallenna!]
  [:div
   [grid/grid
    {:otsikko "TURI-työtunnit"
     :tyhja "Ei TURI-työtunteja."
     :tallenna tallenna!
     :voi-lisata? false
     :voi-poistaa? (constantly false)
     :tunniste :vuosi
     :piilota-toiminnot? true}
    [{:otsikko "Vuosi" :nimi :vuosi :tyyppi :positiivinen-numero :muokattava? (constantly false)}
     {:otsikko "Tammikuu - Huhtikuu" :nimi :ensimmainen-vuosikolmannes :tyyppi :positiivinen-numero}
     {:otsikko "Toukokuu - Elokuu" :nimi :toinen-vuosikolmannes :tyyppi :positiivinen-numero}
     {:otsikko "Syyskuu - Joulukuu" :nimi :kolmas-vuosikolmannes :tyyppi :positiivinen-numero}]
    tyotunnit]])

(defn urakan-tyotunnit [{:keys [id alkupvm loppupvm]}]
  (let [vuodet (reverse (mapv #(hash-map :vuosi %) (pvm/vuodet-valissa alkupvm loppupvm)))
        tyotunnit (atom nil)
        hae! (fn [urakka-id]
               (reset! tyotunnit vuodet)
               (go
                 (let [vastaus (<! (urakan-tyotunnit/hae-urakan-tyotunnit urakka-id))]
                   (if (k/virhe? vastaus)
                     (viesti/nayta! "Urakan työtuntien haku epäonnistui" :warning viesti/viestin-nayttoaika-lyhyt)
                     (reset! tyotunnit (urakan-tyotunnit/tyotunnit-naytettavana vuodet vastaus))))))
        tallenna! (fn [ur]
                    [urakan-tyotuntilista @tyotunnit
                     (when (oikeudet/voi-kirjoittaa? oikeudet/urakat-yleiset (:id ur))
                       (fn [uudet]
                         (go (let [vastaus (<! (urakan-tyotunnit/tallenna-urakan-tyotunnit id uudet))]
                               (if (k/virhe? vastaus)
                                 (viesti/nayta! "Urakan työtuntien tallennus epäonnistui" :warning viesti/viestin-nayttoaika-lyhyt)
                                 (reset! tyotunnit (urakan-tyotunnit/tyotunnit-naytettavana vuodet vastaus)))))))])]
    (hae! id)
    (komp/luo
      (komp/kun-muuttuu (comp hae! :id))
      tallenna!)))

(defn takuuaika [ur]
  (let [tallennus-kaynnissa (atom false)]
    (komp/luo
      (komp/kun-muuttuu #(swap! tallennus-kaynnissa
                                (fn [k]
                                  (if (= k (:id %))
                                    k
                                    false))))
      (fn [ur]
        [:span.takuuaika.inline
         (if (oikeudet/voi-kirjoittaa? oikeudet/urakat-yleiset (:id ur))
           [:span
            [tee-kentta {:tyyppi :pvm :placeholder "Ei asetettu"}
             (r/wrap (get-in ur [:takuu :loppupvm])
                     #(do (reset! tallennus-kaynnissa (:id ur))
                          (nav/paivita-urakan-tiedot! (:id ur) assoc-in [:takuu :loppupvm] %)
                          (go (reset! tallennus-kaynnissa
                                      (if (k/virhe? (<! (urakka/aseta-takuu-loppupvm (:id ur) %)))
                                        :virhe
                                        false)))))]
            (cond
              (number? @tallennus-kaynnissa) [yleiset/ajax-loader-pieni]
              (= :virhe @tallennus-kaynnissa) [:span (ikonit/livicon-warning-sign)]
              :default nil)
            (when (and
                    (get-in ur [:takuu :loppupvm])
                    (not (pvm/jalkeen? (get-in ur [:takuu :loppupvm]) (:loppupvm ur))))
              (yleiset/vihje "Takuu päättyy yleensä urakan päättymisen jälkeen, tarkista päivämäärä"))]
           [:span
            (if-let [p (get-in ur [:takuu :loppupvm])]
              (pvm/pvm p)
              "Ei asetettu")])]))))

(defn- yllapitourakan-sopimustyyppi [ur]
  (when (and (not= :hoito (:tyyppi ur))
             (not= :teiden-hoito (:tyyppi ur))
             (not (u-domain/vesivaylaurakkatyyppi? (:tyyppi ur))))
    (let [kirjoitusoikeus? (oikeudet/voi-kirjoittaa? oikeudet/urakat-yleiset (:id ur))
          sopimustyyppi (:sopimustyyppi ur)]
      [yleiset/livi-pudotusvalikko {:class "alasveto-yleiset-tiedot"
                                    :valinta sopimustyyppi
                                    :format-fn #(if %
                                                  (str/capitalize (name %))
                                                  "Ei sopimustyyppiä")
                                    :valitse-fn #(tallenna-sopimustyyppi ur %)
                                    :disabled (not kirjoitusoikeus?)}
       sopimus/+sopimustyypit+])))

(defn yha-tiedot [ur]
  {:yha-tuontioikeus? (yhatiedot/yha-tuontioikeus? ur)
   :paallystys-tai-paikkausurakka? (or (= (:tyyppi ur) :paallystys)
                                       (= (:tyyppi ur) :paikkaus))
   :paallystys-tai-paikkausurakka-sidottu? (some? (:yhatiedot ur))
   :sidonta-lukittu? (get-in ur [:yhatiedot :sidonta-lukittu?])
   :sidonta-lukittu-vihje "Urakan sidontaa ei voi enää muuttaa, sillä sen tietoja on muutettu."})

(defn- yha-sidonta [ur {:keys [paallystys-tai-paikkausurakka? paallystys-tai-paikkausurakka-sidottu?
                               yha-tuontioikeus?
                               sidonta-lukittu? sidonta-lukittu-vihje]}]
  (cond
    (and paallystys-tai-paikkausurakka? (not yha-tuontioikeus?) (not paallystys-tai-paikkausurakka-sidottu?))
    [:span.bold "Urakanvalvojan täytyy sitoa urakka YHA-urakkaan"]
    (and paallystys-tai-paikkausurakka? yha-tuontioikeus? (not paallystys-tai-paikkausurakka-sidottu?))
    [:span (when sidonta-lukittu? {:title sidonta-lukittu-vihje})
     [:button.nappi-ensisijainen {:on-click #(yha/nayta-tuontidialogi ur)
                                  :disabled sidonta-lukittu?}
      "Sido YHA-urakkaan"]]
    (and paallystys-tai-paikkausurakka? (not yha-tuontioikeus?) paallystys-tai-paikkausurakka-sidottu?)
    [:span "Sidottu YHA-urakkaan. Vain urakanvalvoja voi muuttaa sidontaa."]
    (and paallystys-tai-paikkausurakka? yha-tuontioikeus? paallystys-tai-paikkausurakka-sidottu?)
    [:span (when sidonta-lukittu? {:title sidonta-lukittu-vihje})
     [:button.nappi-ensisijainen {:on-click #(yha/nayta-tuontidialogi ur)
                                  :disabled sidonta-lukittu?}
      "Vaihda sidottu urakka"]]
    :default nil))

(defn- yllapidon-urakkatyypin-vaihto [ur {:keys [paallystys-tai-paikkausurakka? sidonta-lukittu?
                                                 yha-tuontioikeus?]}]
  (when paallystys-tai-paikkausurakka?
    [:span {:title (cond sidonta-lukittu?
                         "Urakan sidonta on lukittu, urakkatyyppiä ei voi enää muuttaa."
                         (not yha-tuontioikeus?)
                         "Vain urakanvalvoja voi muuttaa urakan tyyppiä"
                         :default nil)}
     [yleiset/livi-pudotusvalikko {:class "alasveto-yleiset-tiedot"
                                   :valinta (:tyyppi ur)
                                   :format-fn #(navigaatio/nayta-urakkatyyppi %)
                                   :valitse-fn #(vahvista-urakkatyypin-vaihtaminen ur %)
                                   ;; todo: Urakkatyypin vaihto on toiseseksi estetty,
                                   ;; sillä paikkausurakoiden toteutus on kesken.
                                   :disabled (constantly true)
                                   #_(or (not yha-tuontioikeus?)
                                         sidonta-lukittu?)}
      [:paallystys :paikkaus]]]))

(defn- urakan-indeksi [ur]
  (let [auki? (atom false)]
    (fn [ur]
      [:span
       (if (:indeksi ur)
         (str (:indeksi ur)
              (when (:indeksilaskennan_perusluku ur)
                (str ", perusluku: " (:indeksilaskennan_perusluku ur))))
         "Ei käytössä")
       (when (and (:indeksi ur)
                  (roolit/tilaajan-kayttaja? @istunto/kayttaja)
                  (oikeudet/voi-kirjoittaa? oikeudet/urakat-yleiset (:id ur)))
         [:span
          [:span.klikattava {:on-click #(swap! auki? not)}
           " "
           (ikonit/livicon-wrench)
           " "]
          (when @auki?
            [napit/yleinen-toissijainen "Poista indeksi käytöstä"
             (fn []
               (modal/nayta! {}
                             [:div
                              [:b "Haluatko poistaa indeksin käytön tässä urakassa?"]
                              [yleiset/tietoja {}
                               "Urakka: " (:nimi ur)
                               "Nykyinen indeksi: " (:indeksi ur)]
                              [:div
                               [:br]
                               [napit/palvelinkutsu-nappi "Poista indeksi käytöstä"
                                #(urakat/poista-indeksi-kaytosta! ur)
                                {:luokka "nappi-kielteinen"
                                 :kun-onnistuu #(do (nav/paivita-urakan-tiedot! (:id ur)
                                                                                assoc :indeksi nil)
                                                    (modal/piilota!)
                                                    (reset! auki? false))}]
                               [napit/yleinen-toissijainen "Peruuta" #(do (modal/piilota!)
                                                                          (reset! auki? false))
                                {:luokka "nappi-toissijainen pull-right"}]]]))
             {:luokka "nappi-kielteinen btn-xs"}])])])))


(defn- aseta-vastuuhenkilo [paivita-vastuuhenkilot!
                            urakka-id kayttaja kayttajat vastuuhenkilot rooli
                            ensisijainen varalla]
  (r/with-let [henkilot (atom {:ensisijainen :ei-muutosta
                               :varalla :ei-muutosta})]
    ;; FIXME: valitse oletushenkilöksi nykyinen käyttäjänimen perusteella
    (let [mahdolliset-henkilot (filter #(some (partial = rooli) (:roolinimet %)) kayttajat)]

      [:div.vastuuhenkilo-muokkaus
       [lomake/lomake {:muokkaa! #(reset! henkilot %)
                       :footer-fn (fn [data]
                                    [napit/palvelinkutsu-nappi "Tallenna yhteyshenkilöt"
                                     #(let [{uusi-ensisijainen :ensisijainen
                                             uusi-varalla :varalla} @henkilot]
                                        (tiedot/tallenna-urakan-vastuuhenkilot-roolille
                                          urakka-id rooli
                                          (if (= :ei-muutosta uusi-ensisijainen)
                                            ensisijainen
                                            uusi-ensisijainen)
                                          (if (= :ei-muutosta uusi-varalla)
                                            varalla
                                            uusi-varalla)))
                                     {:kun-onnistuu #(do
                                                       (paivita-vastuuhenkilot! %)
                                                       (modal/piilota!))}])}
        [{:otsikko "Ensisijainen"
          :nimi :ensisijainen
          :leveys 2
          :tyyppi :valinta
          :valinta-nayta #(if (= :ei-muutosta %)
                            (fmt/kayttaja ensisijainen)
                            (fmt/kayttaja %))
          :valinnat mahdolliset-henkilot}
         {:otsikko "Varalla"
          :nimi :varalla
          :leveys 2
          :tyyppi :valinta
          :valinta-nayta #(if (= :ei-muutosta %)
                            (fmt/kayttaja varalla)
                            (fmt/kayttaja %))
          :valinnat mahdolliset-henkilot}]
        @henkilot]])))

(defn- vastuuhenkilo-tooltip [vastuuhenkilo]
  [:span
   [:div "Puhelin: " (:puhelin vastuuhenkilo)]
   [:div "Sähköposti: " (:sahkoposti vastuuhenkilo)]])

(defn- nayta-vastuuhenkilo [paivita-vastuuhenkilot!
                            urakka-id kayttaja kayttajat vastuuhenkilot rooli]
  (let [roolin-henkilot (filter #(= rooli (:rooli %)) vastuuhenkilot)
        ensisijainen (first (filter :ensisijainen roolin-henkilot))
        varalla (first (filter (comp not :ensisijainen) roolin-henkilot))
        voi-muokata? (and (not (k/virhe? kayttajat))
                          (not (empty? kayttajat))
                          (oikeudet/voi-kirjoittaa? oikeudet/urakat-yleiset urakka-id)
                          (or (not= rooli "ELY_Urakanvalvoja")
                              (= :tilaaja (roolit/osapuoli kayttaja))))]
    [:div.vastuuhenkilo.inline-block
     [:span
      (if ensisijainen
        [yleiset/tooltip {}
         [:span.vastuuhenkilo-ensisijainen (fmt/kayttaja ensisijainen)]
         [vastuuhenkilo-tooltip ensisijainen]]
        [:span.vastuuhenkilo-ei-tiedossa "Ei tiedossa"])
      " "
      (when varalla
        [yleiset/tooltip {}
         [:span.vastuuhenkilo-varalla "(sijainen " (fmt/kayttaja varalla) ")"]
         [vastuuhenkilo-tooltip varalla]])
      (when voi-muokata?
        [:span.klikattava {:on-click #(modal/nayta!
                                        {:otsikko (str "Urakan ensisijainen "
                                                       (case rooli
                                                         ("ELY_Urakanvalvoja" "Tilaajan_Urakanvalvoja") "urakanvalvoja"
                                                         "vastuuhenkilo" "vastuuhenkilö"))}
                                        [aseta-vastuuhenkilo
                                         paivita-vastuuhenkilot!
                                         urakka-id kayttaja kayttajat
                                         vastuuhenkilot rooli
                                         ensisijainen varalla])}
         " "
         (ikonit/livicon-wrench)
         " "])]]))

(defn yleiset-tiedot [paivita-vastuuhenkilot! ur kayttajat vastuuhenkilot]
  (let [{:keys [paallystys-tai-paikkausurakka? paallystys-tai-paikkausurakka-sidottu?]
         :as yha-tiedot} (yha-tiedot ur)]
    [bs/panel {}
     "Yleiset tiedot"
     [yleiset/tietoja {}
      "Urakan nimi:" (:nimi ur)
      "Urakan tunnus:" (:sampoid ur)
      "Urakkanumero:" (:urakkanro ur)

      "YHA:n urakkatunnus:"
      (when (and paallystys-tai-paikkausurakka? paallystys-tai-paikkausurakka-sidottu?)
        (get-in ur [:yhatiedot :yhatunnus]))
      "YHA:n ELY:t"
      (when (and paallystys-tai-paikkausurakka? paallystys-tai-paikkausurakka-sidottu?)
        (str/join ", " (get-in ur [:yhatiedot :elyt])))
      "YHA:n vuodet:"
      (when (and paallystys-tai-paikkausurakka? paallystys-tai-paikkausurakka-sidottu?)
        (str/join ", " (get-in ur [:yhatiedot :vuodet])))
      "YHA-sidonta:" (yha-sidonta ur yha-tiedot)

      "Sopimuksen tunnus: " (some->> ur :sopimukset vals (str/join ", "))
      "Aikaväli:" [:span.aikavali (pvm/pvm (:alkupvm ur)) " \u2014 " (pvm/pvm (:loppupvm ur))]
      "Takuu päättyy:" (when paallystys-tai-paikkausurakka?
                         [takuuaika ur])
      "Tilaaja:" (if (u/vesivaylaurakka? ur) "Väylä" (:nimi (:hallintayksikko ur)))
      "Urakanvalvoja: " [nayta-vastuuhenkilo paivita-vastuuhenkilot!
                         (:id ur) @istunto/kayttaja kayttajat vastuuhenkilot
                         (if (u/vesivaylaurakka? ur)
                           "Tilaajan_Urakanvalvoja"
                           "ELY_Urakanvalvoja")]

      "Urakoitsija:" (:nimi (:urakoitsija ur))
      "Urakan vastuuhenkilö: " [nayta-vastuuhenkilo paivita-vastuuhenkilot!
                                (:id ur) @istunto/kayttaja kayttajat vastuuhenkilot "vastuuhenkilo"]

      ;; valaistus, tiemerkintä --> palvelusopimus
      ;; päällystys --> kokonaisurakka
      "Sopimustyyppi: " (yllapitourakan-sopimustyyppi ur)

      "Urakkatyyppi: " ; Päällystysurakan voi muuttaa paikkaukseksi ja vice versa
      (yllapidon-urakkatyypin-vaihto ur yha-tiedot)

      "Indeksi: " (when-not (#{:paallystys :paikkaus} (:tyyppi ur))
                    [urakan-indeksi ur])]]))


(defn yhteyshenkilot [ur]
  (let [yhteyshenkilot (atom nil)
        yhteyshenkilotyypit (tiedot/urakkatyypin-mukaiset-yhteyshenkilotyypit (:tyyppi ur))
        hae! (fn [ur]
               (reset! yhteyshenkilot nil)
               (go (reset! yhteyshenkilot
                           (filter
                             #(not= "urakoitsijan paivystaja" (:rooli %))
                             (<! (tiedot/hae-urakan-yhteyshenkilot (:id ur)))))))]
    (hae! ur)
    (komp/luo
      (komp/kun-muuttuu hae!)
      (fn [ur]
        [grid/grid
         {:otsikko "Yhteyshenkilöt"
          :tyhja "Ei yhteyshenkilöitä."
          :tallenna (when (oikeudet/voi-kirjoittaa? oikeudet/urakat-yleiset (:id ur))
                      #(tallenna-yhteyshenkilot ur yhteyshenkilot %))}
         [{:otsikko "Rooli" :nimi :rooli :tyyppi :valinta :leveys 17
           :hae #(do (when (:rooli %)
                       (str/capitalize (:rooli %))))
           :valinta-nayta #(if (nil? %) "- valitse -" (str/capitalize %))

           :valinnat (vec yhteyshenkilotyypit)

           :validoi [[:ei-tyhja "Anna yhteyshenkilön rooli"]]}
          {:otsikko "Organisaatio"
           :nimi :organisaatio
           :fmt :nimi
           :leveys 17
           :tyyppi :valinta
           :valinta-nayta #(if % (:nimi %) "- Valitse organisaatio -")
           :valinnat [nil (:urakoitsija ur) (:hallintayksikko ur)]}

          {:otsikko "Nimi" :hae #(if-let [nimi (:nimi %)]
                                   nimi
                                   (str (:etunimi %)
                                        (when-let [suku (:sukunimi %)]
                                          (str " " suku))))
           :pituus-max 64
           :aseta (fn [yht arvo]
                    (assoc yht :nimi arvo))
           :tyyppi :string :leveys 15
           :validoi [[:ei-tyhja "Anna yhteyshenkilön nimi"]]}
          {:otsikko "Puhelin (virka)" :nimi :tyopuhelin :tyyppi :puhelin :leveys 12 :pituus 16}
          {:otsikko "Puhelin (gsm)" :nimi :matkapuhelin :tyyppi :puhelin :leveys 12 :pituus 16}
          {:otsikko "Sähköposti" :nimi :sahkoposti :tyyppi :email :leveys 22}]
         @yhteyshenkilot]))))

(defn alukset [ur]
  (let [urakoitsijan-alukset (atom nil)
        muokkausoikeus? (oikeudet/on-muu-oikeus?
                          "alusten-muokkaus"
                          oikeudet/urakat-yleiset
                          (:id @nav/valittu-urakka)
                          @istunto/kayttaja)
        hae-urakoitsijan-alukset (fn [ur]
                                   (reset! urakoitsijan-alukset nil)
                                   (go
                                     (reset! urakoitsijan-alukset
                                               (<! (tiedot/hae-urakoitsijan-alukset
                                                     (:id ur)
                                                     (get-in ur [:urakoitsija :id]))))))]
    (komp/luo
      (komp/sisaan #(hae-urakoitsijan-alukset ur))
      (fn [ur]
        (if (nil? @urakoitsijan-alukset)
          [yleiset/ajax-loader]
          [grid/grid
           {:otsikko "Urakoitsijan alukset"
            :tyhja "Ei aluksia"
            :esta-poistaminen?
            (fn [rivi]
              (let [alus-kaytossa-urakoissa (::alus/kaytossa-urakoissa rivi)
                    kaytossa-muissa-urakoissa (set (remove #(= % (:id ur))
                                                           alus-kaytossa-urakoissa))]

                (or (::alus/kaytossa-urakassa? rivi)
                    (> (count kaytossa-muissa-urakoissa) 0))))
            :esta-poistaminen-tooltip (fn [_] "Alus on käytössä urakoissa.")
            :tunniste :grid-id
            :muutos (fn [g]
                      (let [vaatii-kayton-lisatietojen-tyhjennyksen?
                            (some
                              #(and (not (::alus/kaytossa-urakassa? %))
                                    (some? (::alus/urakan-aluksen-kayton-lisatiedot %)))
                              (vals (grid/hae-muokkaustila g)))]
                        (when vaatii-kayton-lisatietojen-tyhjennyksen?
                          (grid/muokkaa-rivit!
                            g
                            (fn [rivit]
                              (map (fn [rivi]
                                     (if-not (::alus/kaytossa-urakassa? rivi)
                                       (assoc rivi ::alus/urakan-aluksen-kayton-lisatiedot nil)
                                       rivi))
                                   rivit))))))
            :tallenna (when muokkausoikeus?
                        (fn [alukset]
                          (tiedot/tallenna-urakan-alukset (:id ur)
                                                          (get-in ur [:urakoitsija :id])
                                                          alukset
                                                          urakoitsijan-alukset)))}
           [{:otsikko "MMSI"
             :nimi ::alus/mmsi
             :tyyppi :positiivinen-numero
             :leveys 7
             ;; MMSI:n muokkaus ei ole tuettua, jos alus on jo tallennettu, sillä se
             ;; triggeröisi uuden aluksen tallennuksen.
             :muokattava? (fn [rivi] (neg? (:grid-id rivi)))
             :validoi [[:ei-tyhja "Anna MMSI"]
                       [:uniikki "MMSI on jo käytössä"]]}
            {:otsikko "Nimi"
             :nimi ::alus/nimi
             :tyyppi :string
             :leveys 7
             :pituus-max 512}
            {:otsikko "Lisätiedot"
             :nimi ::alus/lisatiedot
             :tyyppi :string
             :leveys 10
             :pituus-max 512}
            {:otsikko "Käytössä tässä urakassa"
             :nimi ::alus/kaytossa-urakassa?
             :tyyppi :checkbox
             :tasaa :keskita
             :fmt fmt/totuus
             :leveys 5}
            {:otsikko "Käyttötarve urakassa"
             :nimi ::alus/urakan-aluksen-kayton-lisatiedot
             :muokattava? (fn [rivi]
                            (::alus/kaytossa-urakassa? rivi))
             :tyyppi :string
             :leveys 10
             :pituus-max 512}]
           ;; Generoidaan gridin riveille id mmsi:n perusteella, joka on uniikki.
           ;; Ei käytetä mmsi:tä suoraan gridissä tunnisteena, sillä
           ;; muuten gridi generoi uuden mmsi:n automaattisesti itse
           (map #(assoc % :grid-id (::alus/mmsi %)) @urakoitsijan-alukset)])))))

(defn- nayta-yha-tuontidialogi-tarvittaessa
  "Näyttää YHA-tuontidialogin, jos tarvii."
  [urakka]
  (let [yha-tuontioikeus? (yhatiedot/yha-tuontioikeus? urakka)
        paallystys-tai-paikkausurakka? (or (= (:tyyppi urakka) :paallystys)
                                           (= (:tyyppi urakka) :paikkaus))
        paallystys-tai-paikkausurakka-sidottu? (some? (:yhatiedot urakka))
        sidonta-lukittu? (get-in urakka [:yhatiedot :sidonta-lukittu?])
        palvelusopimus? (= :palvelusopimus (:sopimustyyppi urakka))]
    (when (and yha-tuontioikeus?
               paallystys-tai-paikkausurakka?
               (not paallystys-tai-paikkausurakka-sidottu?)
               (not sidonta-lukittu?)
               (not palvelusopimus?))
      (yha/nayta-tuontidialogi urakka))))

(defn yleiset [ur]
  (let [kayttajat (atom nil)
        vastuuhenkilot (atom nil)
        hae! (fn [urakan-tiedot]
               (reset! kayttajat nil)
               (reset! vastuuhenkilot nil)
               (go (reset! kayttajat (<! (tiedot/hae-urakan-kayttajat (:id urakan-tiedot)))))
               (go (reset! vastuuhenkilot (<! (tiedot/hae-urakan-vastuuhenkilot (:id urakan-tiedot)))))
               (when (= :paallystys (:tyyppi ur))
                 (reset! urakka/paallystysurakan-indeksitiedot nil)
                 (go (reset! urakka/paallystysurakan-indeksitiedot
                             (<! (indeksit/hae-paallystysurakan-indeksitiedot (:id urakan-tiedot)))))))]
    (hae! ur)
    (komp/luo
      (komp/kun-muuttuu hae!)
      (komp/sisaan (fn [_]
                     (nayta-yha-tuontidialogi-tarvittaessa ur)))
      (fn [ur]
        [:div
         [yleiset-tiedot #(reset! vastuuhenkilot %) ur @kayttajat @vastuuhenkilot]
         (when (= :paallystys (:tyyppi ur))
           [paallystys-indeksit/paallystysurakan-indeksit ur])
         [urakkaan-liitetyt-kayttajat @kayttajat]
         [yhteyshenkilot ur]
         (when (urakka-domain/vesivaylaurakka-ei-kanava? ur)
           [alukset ur])
         (when (urakka/paivystys-kaytossa? ur)
           [paivystajat/paivystajat ur])
         (when (istunto/ominaisuus-kaytossa? :urakan-tyotunnit)
           [urakan-tyotunnit ur])]))))
