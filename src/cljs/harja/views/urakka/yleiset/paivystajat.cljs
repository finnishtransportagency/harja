(ns harja.views.urakka.yleiset.paivystajat
  "Urakan yleiset välilehden päivystäjälista ja aikajana."
  (:require [reagent.core :as r]
            [harja.ui.grid :as grid]
            [harja.pvm :as pvm]
            [cljs-time.core :as t]
            [harja.ui.komponentti :as komp]
            [harja.domain.oikeudet :as oikeudet]
            [cljs.core.async :refer [<!]]
            [clojure.string :as str]
            [harja.ui.viesti :as viesti]
            [harja.ui.yleiset :as yleiset]
            [harja.tiedot.urakka :as urakka-tiedot]
            [harja.tiedot.urakka.yleiset :as tiedot]
            [harja.fmt :as fmt]
            [harja.loki :refer [log]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.kentat :as kentat]
            [harja.ui.aikajana :as aikajana]
            [harja.ui.on-off-valinta :as on-off]
            [harja.ui.protokollat :as protokollat])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def yhteyshenkilot (r/atom []))
(def yhteyshenkilot-haettu? (r/atom false))
(def paivystajaksi-merkityt (r/atom nil))

(defn tallenna-paivystajat [ur paivystajat uudet-paivystajat]
  (log "tallenna päivystäjät!" (pr-str uudet-paivystajat))
  (go (let [tallennettavat
            (into []
                  ;; Kaikki tiedon mankelointi ennen lähetystä tähän
                  (comp (filter #(not (:poistettu %)))
                    (map #(let [rivi (if-let [nimi (:nimi %)]
                                       (let [[_ etu suku] (re-matches #"^ *([^ ]+)( *.*?) *$" nimi)]
                                         (assoc %
                                           :etunimi (str/trim etu)
                                           :sukunimi (str/trim suku)))
                                       %)]
                            ;; Säilytetään yhteyshenkilo_id jos se on olemassa
                            (if (:yhteyshenkilo_id rivi)
                              rivi
                              (dissoc rivi :yhteyshenkilo_id)))))
                  uudet-paivystajat)
            poistettavat
            (into []
                  (keep #(when (and (:poistettu %)
                                    (> (:id %) 0))
                           (:id %)))
                  uudet-paivystajat)
            vastaus (<! (tiedot/tallenna-urakan-paivystajat (:id ur) tallennettavat poistettavat))]
        (if (or (k/virhe? vastaus) (get-in vastaus [:vastaus :virhe]))
          (viesti/nayta! (str "Päivystäjien tallennus epäonnistui.\n\n"
                              (get-in vastaus [:vastaus :virhe]))
                         :warning viesti/viestin-nayttoaika-pitka)
          (do (reset! paivystajat (reverse (sort-by :loppu vastaus)))
              ;; Nollataan yhteyshenkilot-haettu? jotta seuraavalla kerralla haetaan uudet tiedot
              (reset! yhteyshenkilot-haettu? false)
              true)))))

(defn- paivystys-voimassa?
  [paivystys]
  (and (pvm/ennen? (:alku paivystys) (pvm/nyt))
       (pvm/ennen? (pvm/nyt) (:loppu paivystys))))

(def yhteyshenkilohaku
  (reify protokollat/Haku
    (hae [_ teksti]
      (go (let [yhteyshenkilot @yhteyshenkilot
                itemit (if (< (count teksti) 1)
                         yhteyshenkilot
                         (filter #(and
                                    (str (:etunimi %) " " (:sukunimi %))
                                    (not= (.indexOf (.toLowerCase (str (:etunimi %) " " (:sukunimi %)))
                                            (.toLowerCase teksti)) -1))
                           yhteyshenkilot))]
            (vec (sort-by :sukunimi itemit)))))))

(defn paivystajalista
  [ur paivystajat tallenna!]
  (let [varoita-paivystyksen-puuttumisesta? (and
                                              ;; Ennen kuin päivystäjät on haettu, tämä on nil. Älä näytä silloin varoitusta
                                              (not (nil? paivystajat))
                                              (urakka-tiedot/urakka-kaynnissa? ur)
                                              (not-any? #(paivystys-voimassa? %) paivystajat))]
    [:div
     [grid/grid
      {:otsikko "Päivystystiedot"
       :tyhja "Ei päivystystietoja."
       :tallenna tallenna!
       :peruuta (fn []
                  ;; Nollataan yhteyshenkilot-haettu? kun muokkaus perutaan
                  (reset! yhteyshenkilot-haettu? false))
       :uusi-rivi (fn [rivi]
                    ;; Hae yhteyshenkilöt kun ensimmäinen uusi rivi luodaan
                    (when-not @yhteyshenkilot-haettu?
                      (reset! yhteyshenkilot-haettu? true)
                      (go
                        (reset! yhteyshenkilot
                          (<! (tiedot/hae-urakan-yhteyshenkilot (:id ur))))))
                    rivi)
       :rivin-luokka #(when (paivystys-voimassa? %)
                        " bold")}
      [{:otsikko "Nimi"
        :hae #(if-let [nimi (:nimi %)]
                nimi
                (str (:etunimi %)
                  (when-let [suku (:sukunimi %)]
                    (str " " suku))))
        :aseta (fn [rivi valittu]
                 (if (map? valittu)
                   ;; Valittiin urakkaan liitetty yhteyshenkilö
                   (-> rivi
                     (assoc :nimi (str (:etunimi valittu) " " (:sukunimi valittu)))
                     (assoc :etunimi (:etunimi valittu))
                     (assoc :sukunimi (:sukunimi valittu))
                     (assoc :yhteyshenkilo_id (:id valittu))
                     (assoc :sahkoposti (:sahkoposti valittu))
                     (assoc :tyopuhelin (:tyopuhelin valittu))
                     (assoc :matkapuhelin (:matkapuhelin valittu))
                     (assoc :organisaatio (:organisaatio valittu)))
                   ;; Luodaan urakan ulkopuolinen päivystäjä
                   (assoc rivi :nimi valittu)))
        :tyyppi :haku
        :salli-kirjoitus? true
        :lahde yhteyshenkilohaku
        :leveys 15
        :hae-kun-yli-n-merkkia 0
        :piilota-dropdown? true
        :piilota-checkbox? true
        :hakuikoni? false
        :piilota-haetaan-teksti-ja-spinner? true
        :nayta #(if (map? %) (str (:etunimi %) " " (:sukunimi %)) %)
        :muokattava? (fn [rivi _] (nil? (:yhteyshenkilo_id rivi)))
        :validoi [[:ei-tyhja "Anna päivystäjän nimi"]]}
       {:otsikko "Organisaatio" :nimi :organisaatio :fmt :nimi :leveys 10
        :tyyppi :valinta
        :valinta-nayta #(if % (:nimi %) "- Valitse organisaatio -")
        :valinnat [nil (:urakoitsija ur) (:hallintayksikko ur)]
        :muokattava? (fn [rivi _] (nil? (:yhteyshenkilo_id rivi)))}
       {:otsikko "Puhelin (virka)" :nimi :tyopuhelin :tyyppi :puhelin :leveys 10
        :pituus 16 :muokattava? (fn [rivi _] (nil? (:yhteyshenkilo_id rivi)))}
       {:otsikko "Puhelin (gsm)" :nimi :matkapuhelin :tyyppi :puhelin :leveys 10
        :pituus 16 :muokattava? (fn [rivi _] (nil? (:yhteyshenkilo_id rivi)))}
       {:otsikko "Sähköposti" :nimi :sahkoposti :tyyppi :email :leveys 20
        :validoi [[:email "Kirjoita sähköpostiosoite loppuun ilman ääkkösiä."]]
        :muokattava? (fn [rivi _] (nil? (:yhteyshenkilo_id rivi)))}
       {:otsikko "Alkupvm" :nimi :alku :tyyppi :pvm-aika :fmt pvm/pvm-aika :leveys 10
        :validoi [[:ei-tyhja "Aseta alkupvm"]
                  (fn [alku rivi]
                    (let [id (:id rivi)
                          loppu (:loppu rivi)]
                      (when (and alku loppu
                                 (t/before? loppu alku))
                        "Loppupvm ei voi olla alkua ennen.")
                      (when (and (neg? id)
                                 alku
                                 (t/before? alku (pvm/paivan-alussa-opt (pvm/nyt))))
                        "Et saa asettaa uuden päivystyksen alkamishetkeä menneisyyteen.")))]}
       {:otsikko "Loppupvm" :nimi :loppu :tyyppi :pvm-aika :fmt pvm/pvm-aika :leveys 10
        :validoi [[:ei-tyhja "Aseta loppupvm"]
                  (fn [loppu rivi]
                    (let [alku (:alku rivi)]
                      (when (and alku loppu
                                 (t/before? loppu alku))
                        "Loppupvm ei voi olla alkua ennen.")))]}
       {:otsikko "Vastuuhenkilö" :nimi :vastuuhenkilo :tyyppi :checkbox
        :leveys 10
        :fmt fmt/totuus :tasaa :keskita}]
      paivystajat]
     (if varoita-paivystyksen-puuttumisesta?
       [yleiset/info-laatikko :varoitus
        "Urakka on käynnissä mutta vuorossaoleva päivystäjä puuttuu!"
        "Tieliikenneilmoituksia ei voi toimittaa urakkaan. Ole hyvä ja lisää vuorossaoleva päivystäjä." nil]
       [yleiset/vihje "Kaikista ilmoituksista lähetetään aina kaikille vuorossaoleville päivystäjille tieto sähköpostilla.
   Uusista toimenpidepyynnöistä lähetetään tekstiviesti vain vuorossaoleville vastuuhenkilöille"])]))

(defn- aikajanariveiksi
  "Muunna päivystäjälista aikajanriveiksi. Ryhmitellään tiedot nimen ja organisaation mukaan,
  joten yhdelle päivystäjälle tulee vain yksi uimarata, vaikka hänellä olisi useita
  eri päivystysvuoroja."
  [paivystajat vain-urakoitsijat?]
  (let [ryhmitellyt-paivystykset (->> paivystajat
                                      (group-by (juxt :etunimi :sukunimi :organisaatio))
                                      (sort-by (fn [[[etunimi sukunimi {organisaation-nimi :nimi}] _]]
                                                 [etunimi sukunimi organisaation-nimi])))]
    (for [[[etunimi sukunimi org] paivystykset] ryhmitellyt-paivystykset
          :when (or (not vain-urakoitsijat?)
                    (= :urakoitsija (:tyyppi org)))]
      {::aikajana/otsikko (str etunimi " " sukunimi)
       ::aikajana/ajat (for [{:keys [alku loppu varahenkilo vastuuhenkilo]} paivystykset]
                         {::aikajana/alku alku
                          ::aikajana/loppu loppu
                          ::aikajana/teksti (str (pvm/pvm-aika alku) " \u2013 "
                                                 (pvm/pvm-aika loppu))
                          ::aikajana/vari (cond varahenkilo "yellow"
                                                vastuuhenkilo "green"
                                                :default "gray")})})))

(defn- aikajana-valinnat []
  [:div
   [kentat/tee-kentta {:tyyppi :toggle
                       :paalle-teksti "Näytä päivystäjien aikajana"
                       :pois-teksti "Piilota aikajana"
                       :toggle! tiedot/toggle-nayta-aikajana!} tiedot/nayta-aikajana?]

   (when @tiedot/nayta-aikajana?
     [on-off/on-off "Kaikki" "Vain urakoitsijan henkilöt"
      @tiedot/aikajana-vain-urakoitsijat? tiedot/toggle-aikajana-vain-urakoitsijat!])])

(defn- aikajana [paivystajat]
  [:div.paivystys-aikajana
   [aikajana-valinnat]

   (when @tiedot/nayta-aikajana?
     [:div.paivystajat-aikajana
      [aikajana/aikajana
       {::aikajana/alku (pvm/paivaa-sitten 14)
        ::aikajana/loppu (pvm/paivaa-sitten -60)}
       (aikajanariveiksi paivystajat @tiedot/aikajana-vain-urakoitsijat?)]])])

(defn paivystajat [ur]
  (let [hae! (fn [urakka-id]
               (reset! yhteyshenkilot nil)
               (reset! yhteyshenkilot-haettu? false)
               (log "HAETAAN PÄIVYSTÄJÄT JA YHTEYSHENKILÖT: " urakka-id)
               (go (reset! paivystajaksi-merkityt
                           (reverse (sort-by :loppu
                                             (<! (tiedot/hae-urakan-paivystajat urakka-id))))))
               (go (reset! yhteyshenkilot
                     (<! (tiedot/hae-urakan-yhteyshenkilot urakka-id)))))]
    (hae! (:id ur))
    (komp/luo
      (komp/kun-muuttuu (comp hae! :id))
      (fn [ur]
        [:div.paivystajat
         [paivystajalista ur @paivystajaksi-merkityt
          (when (oikeudet/voi-kirjoittaa? oikeudet/urakat-yleiset (:id ur))
            #(tallenna-paivystajat ur paivystajaksi-merkityt %))]
         [aikajana @paivystajaksi-merkityt]]))))
