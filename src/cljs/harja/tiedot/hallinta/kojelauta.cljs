(ns harja.tiedot.hallinta.kojelauta
  (:require [clojure.string :as clj-str]
            [harja.fmt :as fmt]
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
                            :ely nil
                            :urakat nil
                            :urakkavuosi (pvm/vuosi (first (pvm/paivamaaran-hoitokausi (pvm/nyt))))}}))

(defn lupaustietojen-yhteenveto
  [urakat]
  (let [kaikkien-urakoiden-lkm (count urakat)
        urakat-joissa-pisteet-syotetty (count (filter (fn [rivi]
                                                        (integer? (:lupaus_tavoitepisteet rivi)))
                                                urakat))
        urakat-joista-tieto-puuttuu (- kaikkien-urakoiden-lkm urakat-joissa-pisteet-syotetty)
        lupauspisteiden-yhteenveto (when-not (empty? urakat)
                                      [:span.valikatselmustiedot
                                       [yleiset/tietoja {:class "body-text"}
                                        "Lupaukset puuttuvat" (str urakat-joista-tieto-puuttuu " (" (fmt/prosentti-opt (* 100 (/ urakat-joista-tieto-puuttuu kaikkien-urakoiden-lkm))) ")")
                                        "Lupaukset kirjattu:" (str urakat-joissa-pisteet-syotetty " (" (fmt/prosentti-opt (* 100 (/ urakat-joissa-pisteet-syotetty kaikkien-urakoiden-lkm))) ")")]])]
    lupauspisteiden-yhteenveto))

(defn valikatselmus-tilojen-yhteenveto
  "Palauttaa käyttöliittymän koosteriville välikatselmuksen tilojen yhteenvedon"
  [urakat]
  (let [kaikkien-urakoiden-lkm (count urakat)
        urakat-joissa-tavoitehintapaatos (count (filter (fn [rivi]
                                                          (some? (:tavoitehintapaatos rivi))) urakat))
        urakat-joissa-jokin-lupauspaatos-tehtyna (count (keep (fn [rivi]
                                                                (seq (:lupauspaatokset rivi))) urakat))
        urakat-joissa-ei-paatoksia (count (filter (fn [rivi]
                                                    (and
                                                      (nil? (:tavoitehintapaatos rivi))
                                                      (nil? (:kattohintapaatos rivi))
                                                      (nil? (:lupauspaatokset rivi)))) urakat))
        valikatselmusten-yhteenveto (when-not (empty? urakat)
                                      [:span.valikatselmustiedot
                                       [yleiset/tietoja {:class "body-text"}
                                        "Ei yhtään päätöstä:" (str urakat-joissa-ei-paatoksia " (" (fmt/prosentti-opt (* 100 (/ urakat-joissa-ei-paatoksia kaikkien-urakoiden-lkm))) ")")
                                        "Tavoite\u00ADhinta\u00ADpäätös:" (str urakat-joissa-tavoitehintapaatos " (" (fmt/prosentti-opt (* 100 (/ urakat-joissa-tavoitehintapaatos kaikkien-urakoiden-lkm))) ")")
                                        "Lupaus\u00ADpäätös:" (str urakat-joissa-jokin-lupauspaatos-tehtyna " (" (fmt/prosentti-opt (* 100 (/ urakat-joissa-jokin-lupauspaatos-tehtyna kaikkien-urakoiden-lkm))) ")")]])]
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
                                  "Aloittamatta:" (str urakat-joissa-ks-aloittamatta " (" (fmt/prosentti-opt (* 100 (/ urakat-joissa-ks-aloittamatta kaikkien-urakoiden-lkm))) ")")
                                  "Kesken:" (str urakat-joissa-ks-aloitettu " (" (fmt/prosentti-opt (* 100 (/ urakat-joissa-ks-aloitettu kaikkien-urakoiden-lkm))) ")")
                                  "Valmiina:" (str urakat-joissa-ks-valmiina " (" (fmt/prosentti-opt (* 100 (/ urakat-joissa-ks-valmiina kaikkien-urakoiden-lkm))) ")")]])]
    ks-tilojen-yhteenveto))

(defrecord AsetaSuodatin [avain valinta])
(defrecord HaeUrakat [])
(defrecord HaeUrakatOnnistui [vastaus])
(defrecord HaeUrakatEpaonnistui [vastaus])

(extend-protocol tuck/Event
  AsetaSuodatin
  (process-event [{:keys [avain valinta]} app]
    (assoc-in app [:valinnat avain] valinta))

  HaeUrakat
  (process-event [_ app]
    (tuck-apurit/post! :hae-urakat-kojelautaan
      {:urakkatyyppi (or (get-in app [:valinnat :urakkatyyppi :arvo]) :hoito)
       :hoitokauden-alkuvuosi (get-in app [:valinnat :urakkavuosi])
       :urakka-idt (map :id (get-in app [:valinnat :urakat]))
       :ely-id (get-in app [:valinnat :ely :id])}
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
