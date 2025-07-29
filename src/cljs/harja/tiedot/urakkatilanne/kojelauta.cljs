(ns harja.tiedot.urakkatilanne.kojelauta
  (:require [harja.fmt :as fmt]
            [harja.math :as math]
            [harja.pvm :as pvm]
            [harja.ui.viesti :as viesti]
            [harja.ui.protokollat :as protokollat]
            [harja.ui.yleiset :as yleiset]
            [reagent.core :refer [atom] :as reagent]
            [tuck.core :as tuck]
            [harja.tyokalut.tuck :as tuck-apurit])
  (:require-macros [cljs.core.async.macros :refer [go]]))

;; vuosi, jota ennen tavoite ja kattohinnan paatokset eivat olleet sidoksissa toisiinsa
(def +kattohintapaatos-kynnysvuosi+ 2021)

(defn tee-elyhaku [elyt]
  (reify protokollat/Haku
    (hae [_ teksti]
      (go (let [itemit (if (< (count teksti) 1)
                         elyt
                         (filter #(and
                                    (str (:elynumero %) " " (:nimi %))
                                    (not= (.indexOf (.toLowerCase (str (:elynumero %) " " (:nimi %)))
                                            (.toLowerCase teksti)) -1))
                           elyt))]
            (vec (sort-by :elynumero itemit)))))))

(defn tee-urakkahaku [urakat]
  (reify protokollat/Haku
    (hae [_ teksti]
      (go (let [itemit (if (< (count teksti) 1)
                         urakat
                         (filter #(and
                                    (:nimi %)
                                    (not= (.indexOf (.toLowerCase (:nimi %))
                                            (.toLowerCase teksti)) -1))
                           urakat))]
            (vec (sort-by :nimi itemit)))))))

(def tila (atom {:urakat []
                 :valinnat {:urakkatyyppi {:nimi "Hoito" :arvo :hoito}
                            :ely-idt #{}
                            :urakat #{}
                            :urakkavuosi (pvm/vuosi (first (pvm/paivamaaran-hoitokausi (pvm/nyt))))}}))

(defn paallystystietojen-yhteenveto [urakat]
  (let [kohteiden-lukumaara (reduce + 0 (map :yllapitokohteiden_lkm urakat))]
    [yleiset/tietoja {:class "body-text"}
     "Kohteita: " (str kohteiden-lukumaara)]))

(defn valmiit-yhteenveto
  [urakat]
  (let [kohteiden-lukumaara (reduce + 0 (map :yllapitokohteiden_lkm urakat))
        valmiit-lkm (reduce + 0 (map :valmis_hyvaksytty urakat))
        valmiit-yhteenveto (when-not (empty? urakat) [:span.valmis_hyvaksytty
                                                      [yleiset/tietoja {:class "body-text"}
                                                       "Valmiit: " (str valmiit-lkm " (" (fmt/prosentti-opt (math/osuus-prosentteina valmiit-lkm kohteiden-lukumaara) 0) ")")]])]
    valmiit-yhteenveto))

(defn lahetetyt-yhteenveto
  [urakat]
  (let [kohteiden-lukumaara (reduce + 0 (map :yllapitokohteiden_lkm urakat))
        lahetetty-lkm (reduce + 0 (map :lahetetty_onnistuneesti urakat))
        lahetetty-yhteenveto (when-not (empty? urakat) [:span.lahetetty_onnistuneesti
                                                        [yleiset/tietoja {:class "body-text"}
                                                         "Lähetetyt: " (str lahetetty-lkm " (" (fmt/prosentti-opt (math/osuus-prosentteina lahetetty-lkm kohteiden-lukumaara) 0) ")")]])]
    lahetetty-yhteenveto))


(defn epaonnistuneet-lahetetyt-yhteenveto
  [urakat]
  (let [kohteiden-lukumaara (reduce + 0 (map :yllapitokohteiden_lkm urakat))
        epaonnistuneet-lkm (reduce + 0 (map :epaonnistuneet_lahetetyt urakat))
        epaonnistuneet-yhteenveto (when-not (empty? urakat) [:span.epaonnistuneet_lahetetyt
                                                             [yleiset/tietoja {:class "body-text"}
                                                              "Epäonnis\u00ADtuneet: " (str epaonnistuneet-lkm " (" (fmt/prosentti-opt (math/osuus-prosentteina epaonnistuneet-lkm kohteiden-lukumaara) 0) ")")]])]
    epaonnistuneet-yhteenveto))

(defn valmiit-ei-lahetetty-yhteenveto
  [urakat]
  (let [kohteiden-lukumaara (reduce + 0 (map :yllapitokohteiden_lkm urakat))
        valmiit-ei-hyvaksytty-lkm (reduce + 0 (map :valmiit_ei_lahetetty urakat))
        valmiit-ei-hyvaksytty-yhteenveto (when-not (empty? urakat) [:span.valmiit_ei_lahetetty
                                                                    [yleiset/tietoja {:class "body-text"}
                                                                     "Ei lähetetty " (str valmiit-ei-hyvaksytty-lkm " (" (fmt/prosentti-opt (math/osuus-prosentteina valmiit-ei-hyvaksytty-lkm kohteiden-lukumaara) 0) ")")]])]
    valmiit-ei-hyvaksytty-yhteenveto))

(defn aloittamatta-yhteenveto
  [urakat]
  (let [kohteiden-lukumaara (reduce + 0 (map :yllapitokohteiden_lkm urakat))
        aloittamatta-lkm (reduce + 0 (map :aloittamatta urakat))
        aloittamatta-yhteenveto (when-not (empty? urakat) [:span.aloittamatta
                                                           [yleiset/tietoja {:class "body-text"}
                                                            "Aloittamatta: " (str aloittamatta-lkm " (" (fmt/prosentti-opt (math/osuus-prosentteina aloittamatta-lkm kohteiden-lukumaara) 0) ")")]])]
    aloittamatta-yhteenveto))

(defn poikkeusten-yhteenveto
  [urakat]
  (let [kaikkien-urakoiden-lkm (count urakat)
        urakat-joissa-avoimia-laatupoikkeamia (count (filter (fn [rivi]
                                                               (pos-int? (:avoimet_laatupoikkeamat rivi)))
                                                       urakat))
        urakat-joissa-avoimia-turvallisuuspoikkeamia (count (filter (fn [rivi]
                                                                      (pos-int? (:avoimet_turvallisuuspoikkeamat rivi)))
                                                              urakat))
        urakat-joissa-ei-poikkeamia (count (filter (fn [rivi]
                                                     (and
                                                       (zero? (:avoimet_laatupoikkeamat rivi))
                                                       (zero? (:avoimet_turvallisuuspoikkeamat rivi)))) urakat))
        poikkeamien-yhteenveto (when-not (empty? urakat)
                                 [:span.valikatselmustiedot
                                  [yleiset/tietoja {:class "body-text"}
                                   "Avoimia laatupoikkeamia:" (str urakat-joissa-avoimia-laatupoikkeamia " (" (fmt/prosentti-opt (math/osuus-prosentteina urakat-joissa-avoimia-laatupoikkeamia kaikkien-urakoiden-lkm) 0) ")")
                                   "Avoimia turvallisuuspoikkeamia:" (str urakat-joissa-avoimia-turvallisuuspoikkeamia " (" (fmt/prosentti-opt (math/osuus-prosentteina urakat-joissa-avoimia-turvallisuuspoikkeamia kaikkien-urakoiden-lkm) 0) ")")
                                   "Ei avoimia poikkeamia: " (str urakat-joissa-ei-poikkeamia " (" (fmt/prosentti-opt (math/osuus-prosentteina urakat-joissa-ei-poikkeamia kaikkien-urakoiden-lkm) 0) ")")]])]
    poikkeamien-yhteenveto))

(defn lupaustietojen-yhteenveto
  [urakat]
  (let [kaikkien-urakoiden-lkm (count urakat)
        urakat-joissa-pisteet-syotetty (count (filter (fn [rivi]
                                                        (and (:toteutuneet_pisteet rivi) (:luvatut_pisteet rivi)))
                                                urakat))
        urakat-joista-tieto-puuttuu (- kaikkien-urakoiden-lkm urakat-joissa-pisteet-syotetty)
        lupauspisteiden-yhteenveto (when-not (empty? urakat)
                                     [:span.lupaustiedot
                                      [yleiset/tietoja {:class "body-text kojelauta-tietoja"}
                                       "Lupaukset puuttuvat" (str urakat-joista-tieto-puuttuu " (" (fmt/prosentti-opt (math/osuus-prosentteina urakat-joista-tieto-puuttuu kaikkien-urakoiden-lkm) 0) ")")
                                       "Lupaukset kirjattu:" (str urakat-joissa-pisteet-syotetty " (" (fmt/prosentti-opt (math/osuus-prosentteina urakat-joissa-pisteet-syotetty kaikkien-urakoiden-lkm) 0) ")")]])]
    lupauspisteiden-yhteenveto))

(defn valikatselmus-tilojen-yhteenveto
  "Palauttaa käyttöliittymän koosteriville välikatselmuksen tilojen yhteenvedon"
  [urakat]
  (let [kaikkien-urakoiden-lkm (count urakat)
        urakat-joissa-tavoitehintapaatos (count (filter (fn [rivi]
                                                          (some? (or (:tavoitehintaalituspaatos rivi) (:tavoitehintaylityspaatos rivi)))) urakat))
        urakat-joissa-jokin-lupauspaatos-tehtyna (count (filter (fn [rivi]
                                                                  (some? (:lupauspaatos rivi))) urakat))
        urakat-joissa-ei-paatoksia (count (filter (fn [rivi]
                                                    (and
                                                      (nil? (:tavoitehintaalituspaatos rivi))
                                                      (nil? (:tavoitehintaylityspaatos rivi))
                                                      (nil? (:kattohintapaatos rivi))
                                                      (nil? (:lupauspaatos rivi)))) urakat))
        valikatselmusten-yhteenveto (when-not (empty? urakat)
                                      [:span.valikatselmustiedot
                                       [yleiset/tietoja {:class "body-text"}
                                        "Ei yhtään päätöstä:" (str urakat-joissa-ei-paatoksia " (" (fmt/prosentti-opt (math/osuus-prosentteina urakat-joissa-ei-paatoksia kaikkien-urakoiden-lkm) 0) ")")
                                        "Tavoite\u00ADhinta\u00ADpäätös:" (str urakat-joissa-tavoitehintapaatos " (" (fmt/prosentti-opt (math/osuus-prosentteina urakat-joissa-tavoitehintapaatos kaikkien-urakoiden-lkm) 0) ")")
                                        "Lupaus\u00ADpäätös:" (str urakat-joissa-jokin-lupauspaatos-tehtyna " (" (fmt/prosentti-opt (math/osuus-prosentteina urakat-joissa-jokin-lupauspaatos-tehtyna kaikkien-urakoiden-lkm) 0) ")")]])]
    valikatselmusten-yhteenveto))

(defn ks-tilojen-yhteenveto
  "Palauttaa käyttöliittymän koosteriville kustannussuunnitelman tilojen yhteenvedon"
  [urakat]
  (let [kaikkien-urakoiden-lkm (count urakat)
        urakat-joissa-ks-aloittamatta (count (filter (fn [rivi]
                                                       (= "aloittamatta" (get-in rivi [:ks_tila :suunnitelman_tila])))
                                               urakat))
        urakat-joissa-ks-aloitettu (count (filter (fn [rivi]
                                                    (= "aloitettu" (get-in rivi [:ks_tila :suunnitelman_tila])))
                                            urakat))
        urakat-joissa-ks-valmiina (count (filter (fn [rivi]
                                                   (= "vahvistettu" (get-in rivi [:ks_tila :suunnitelman_tila])))
                                           urakat))
        ks-tilojen-yhteenveto (when-not (empty? urakat)
                                [:span.kustannussuunnitelmien-tiedot
                                 [yleiset/tietoja {:class "body-text"}
                                  "Aloittamatta:" (str urakat-joissa-ks-aloittamatta " (" (fmt/prosentti-opt (math/osuus-prosentteina urakat-joissa-ks-aloittamatta kaikkien-urakoiden-lkm) 0) ")")
                                  "Kesken:" (str urakat-joissa-ks-aloitettu " (" (fmt/prosentti-opt (math/osuus-prosentteina urakat-joissa-ks-aloitettu kaikkien-urakoiden-lkm) 0) ")")
                                  "Valmiina:" (str urakat-joissa-ks-valmiina " (" (fmt/prosentti-opt (math/osuus-prosentteina urakat-joissa-ks-valmiina kaikkien-urakoiden-lkm) 0) ")")]])]
    ks-tilojen-yhteenveto))

(defrecord AlustaHallintayksikkoHaku [elyt])
(defrecord AsetaSuodatin [avain valinta])
(defrecord HaeUrakat [])
(defrecord HaeUrakatOnnistui [vastaus])
(defrecord HaeUrakatEpaonnistui [vastaus])

(extend-protocol tuck/Event

  AlustaHallintayksikkoHaku
  (process-event [{:keys [elyt]} app]
    (assoc app :elyhaku (tee-elyhaku elyt)))

  AsetaSuodatin
  (process-event [{:keys [avain valinta]} app]
    (assoc-in app [:valinnat avain] valinta))

  HaeUrakat
  (process-event [_ app]
    (tuck-apurit/post! :hae-urakat-kojelautaan
      {:urakkatyyppi (or (get-in app [:valinnat :urakkatyyppi :arvo]) :hoito)
       :hoitokauden-alkuvuosi (get-in app [:valinnat :urakkavuosi])
       :urakka-idt (into #{} (map :id (get-in app [:valinnat :urakat])))
       :ely-idt (into #{} (map :id (get-in app [:valinnat :elyt])))}
      {:onnistui ->HaeUrakatOnnistui
       :epaonnistui ->HaeUrakatEpaonnistui})
    (assoc app :haku-kaynnissa? true))

  HaeUrakatOnnistui
  (process-event [{:keys [vastaus]} app]
    (assoc app
      :haku-kaynnissa? false
      :urakat vastaus
      :urakkahaku (tee-urakkahaku vastaus)))

  HaeUrakatEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! "Virhe urakoiden haussa" :varoitus)
    (assoc app
      :urakat []
      :haku-kaynnissa? false)))
