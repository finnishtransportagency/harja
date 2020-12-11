(ns harja.palvelin.palvelut.kustannusten-seuranta
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelut poista-palvelut]]
            [taoensso.timbre :as log]
            [harja.domain.skeema :refer [Toteuma validoi]]
            [slingshot.slingshot :refer [throw+ try+]]
            [harja.domain.kulut.kustannusten-seuranta :as kustannusten-seuranta]
            [harja.domain.oikeudet :as oikeudet]
            [harja.pvm :as pvm]
            [harja.kyselyt.kustannusten-seuranta :as kustannusten-seuranta-q]
            [harja.domain.roolit :as roolit]
            [harja.palvelin.raportointi.excel :as excel]
            [harja.palvelin.komponentit.excel-vienti :as excel-vienti]))


(defn- hae-urakan-kustannusten-seuranta-paaryhmittain [db user {:keys [urakka-id hoitokauden-alkuvuosi alkupvm loppupvm] :as tiedot}]
  ;; TODO tarkista käyttöoikeudet
  (if (oikeudet/voi-lukea? oikeudet/urakat-toteumat-kokonaishintaisettyot urakka-id user)
    (let [_ (println "hae-urakan-kustannusten-seuranta-toimenpideittain :: tiedot " (pr-str tiedot))
          res (kustannusten-seuranta-q/listaa-kustannukset-paaryhmittain db {:urakka urakka-id
                                                                             :alkupvm alkupvm
                                                                             :loppupvm loppupvm
                                                                             :hoitokauden-alkuvuosi (int hoitokauden-alkuvuosi)})]
      res)
    (throw+ (roolit/->EiOikeutta "Ei oikeutta"))))

(defn- kustannukset-excel
  [db workbook user {:keys [urakka-id urakka-nimi hoitokauden-alkuvuosi alkupvm loppupvm]}]
  ;;TODO: Tarkista käyttöoikeudet
  (oikeudet/voi-lukea? oikeudet/urakat-toteumat-kokonaishintaisettyot urakka-id user)
  (let [alkupvm (or alkupvm
                    (pvm/->pvm "01.01.1990"))
        loppupvm (or loppupvm
                     (pvm/nyt))
        kustannukset-tehtavittain (kustannusten-seuranta-q/listaa-kustannukset-paaryhmittain db {:urakka urakka-id
                                                                       :alkupvm alkupvm
                                                                       :loppupvm loppupvm
                                                                       :hoitokauden-alkuvuosi (int hoitokauden-alkuvuosi)})
        kustannusdata (kustannusten-seuranta/jarjesta-tehtavat kustannukset-tehtavittain)
        _ (println "kustannusdata" (pr-str kustannusdata))
        luo-sarakkeet (fn [& otsikot]
                        (mapv #(hash-map :otsikko %) otsikot))
        sarakkeet (luo-sarakkeet "Toimenpide", "Budjetti €", "Toteuma €", "Erotus €", "%")
        optiot {:nimi  urakka-nimi
                :tyhja (if (empty? kustannukset-tehtavittain) "Ei kustannuksia valitulla aikavälillä.")}

        luo-rivi (fn [rivi]
                   (let [_ (println "luo-rivi :: rivi" (pr-str rivi))]
                     [(:toimenpide rivi)
                      (:toimenpide-budjetoitu-summa rivi)
                      (:toimenpide-toteutunut-summa rivi)
                      "0"
                      "%"]))
        luo-data (fn [kaikki rivit]
                   (conj kaikki
                         [:teksti (str "vuosi-kuukausi")]
                         [:otsikko (str "vuosi-kuukausi")]
                         [:taulukko optiot sarakkeet (mapv luo-rivi rivit)]))
        taulukot (reduce luo-data [] (get-in kustannusdata [:taulukon-rivit :paaryhma :hankintakustannukset] ))
        taulukko (concat
                   [:raportti {:nimi        (str urakka-nimi "_" (pvm/pvm alkupvm) "-" (pvm/pvm loppupvm))
                               :orientaatio :landscape}]
                   (if (empty? taulukot)
                     [[:taulukko optiot (luo-sarakkeet (str urakka-nimi "_" (pvm/pvm alkupvm) "-" (pvm/pvm loppupvm))) [["Ei kustannuksia valitulla aikavälillä"]]]]
                     taulukot))]
    (excel/muodosta-excel (vec taulukko)
                          workbook)))

(defrecord KustannustenSeuranta []
  component/Lifecycle
  (start [this]
    (let [http (:http-palvelin this)
          db (:db this)
          db-replica (:db-replica this)
          excel (:excel-vienti this)]
      (assert (some? db-replica))

      (julkaise-palvelut
        http
        :urakan-kustannusten-seuranta-paaryhmittain
        (fn [user tiedot]
          (hae-urakan-kustannusten-seuranta-paaryhmittain db-replica user tiedot)))
      (when excel
        (excel-vienti/rekisteroi-excel-kasittelija! excel :kustannukset (partial #'kustannukset-excel db)))


      this))

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :urakan-kustannusten-seuranta-paaryhmittain)
    this))
