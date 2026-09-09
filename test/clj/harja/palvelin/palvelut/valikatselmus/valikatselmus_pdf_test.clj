(ns harja.palvelin.palvelut.valikatselmus.valikatselmus-pdf-test
  (:require [clojure.test :refer :all]
            [harja.kyselyt.urakat :as urakat-kyselyt]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]

            [harja.palvelin.palvelut.valikatselmus.valikatselmukset :as valikatselmus-palvelu]
            [harja.palvelin.palvelut.valikatselmus.valikatselmus-pdf :as valikatselmus-pdf]))


(defn http-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :pdf-vienti (component/using
                        (pdf-vienti/luo-pdf-vienti)
                        [:http-palvelin])
          :valikatselmukset (component/using
                              (valikatselmus-palvelu/->Valikatselmukset)
                              [:http-palvelin :db :pdf-vienti])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :once http-fixture)

(def perusdata
  {:hoitokauden-alkuvuosi 2026
   :urakan-parametrit {:muutosten_hallinta true
                       :laskutusraja_kaytossa true
                       :hoitokauden_lopun_kattohinta_kerroin 1.2M}
   :paatokset [{:lupaukset {:id 1 :lupausbonus 100M :lupaussanktio -50M}}
               {:hoidonjohtopalkkion-muutos {:id 2 :hoidonjohtopalkkio_muutos -25M}}
               {:tavoitehinnan-alitus {:id 3 :alituksen_maara 200M :tavoitepalkkio 50M :siirron_maara 75M}}]
   :yhteenveto {:budjettitavoite {:tavoitehinta-indeksikorjattu 1000M
                                  :hoitovuoden-lopun-tavoitehinta 1000M
                                  :hoitovuoden-lopun-kattohinta 1200M
                                  :kirjallisesti-sovitut-muutokset 10M
                                  :menneet-muutos-summa 20M}
                :kustannukset {:hankintakustannukset-toteutunut 500M
                               :rahavaraukset-toteutunut 20M
                               :erillishankinnat-toteutunut 30M
                               :johto-ja-hallintokorvaus-toteutunut 40M
                               :hoidonjohdonpalkkio-toteutunut 50M
                               :muukulu-tavoitehintainen-toteutunut 0M}
                :kustannukset-yhteensa {:yht-toteutunut-summa 800M}
                :toteumiin-perustuvat-muutokset-yht 5M
                :arvonvahennykset [{:maara 15M}]
                :bonukset [{:tyyppi "asiakastyytyvaisyysbonus" :rahasumma 30M}]
                :sanktiot [{:sakkoryhma "laskutus_yli_laskutusrajan" :maara -12M :indeksikorjaus -1M}
                           {:sakkoryhma "A" :maara -8M :indeksikorjaus -2M}]}})

(deftest yhteenveto-vastaa-ui-osioita
  (let [urakka-id (hae-urakan-id-nimella "Raahen MHU 2023-2028")
        urakan-tiedot (first (urakat-kyselyt/hae-urakka (:db jarjestelma) {:id urakka-id}))
        rivit (valikatselmus-pdf/yhteenveto-rivit perusdata urakan-tiedot)
        alitusrivit (:tavoitehinnan-alitus rivit)
        sanktiot (:sanktiot rivit)]
    (is (contains? rivit :bonukset))
    (is (contains? rivit :sanktiot))
    (is (contains? rivit :hoidonjohtopalkkio))
    (is (not (contains? rivit :urakoitsijan-saatavat)))
    (is (not (contains? rivit :tilaajan-saatavat)))
    (is (not (contains? rivit :siirrot)))
    (is (some #(= "Laskutus yli laskutusrajan -sanktiot" (first %)) sanktiot))
    (is (some #(= "Muut sanktiot" (first %)) sanktiot))
    (is (not= (second (some #(when (= "Laskutus yli laskutusrajan -sanktiot" (first %)) %) sanktiot))
              (second (some #(when (= "Muut sanktiot" (first %)) %) sanktiot))))
    (is (= 200M (:tavoitehinnan-alitus-maara rivit)))
    (is (some #(= "Tavoitepalkkio" (first %)) alitusrivit))
    (is (some #(= "Siirto seuraavan vuoden hankintakustannuksiin" (first %)) alitusrivit))
    (is (= "25,00" (second (first (:hoidonjohtopalkkio rivit)))))))

(deftest varillinen-osio-on-kustannusosion-sisalla
  (let [urakka-id (hae-urakan-id-nimella "Raahen MHU 2023-2028")
        urakan-tiedot (first (urakat-kyselyt/hae-urakka (:db jarjestelma) {:id urakka-id}))
        rivit (valikatselmus-pdf/yhteenveto-rivit perusdata urakan-tiedot)
        varillinen-osio (valikatselmus-pdf/varillinen-osio :vihrea (:tavoitehinnan-alitus rivit))
        osio (valikatselmus-pdf/osio "Tavoitehintaan kuuluvat toteutuneet kustannukset" (:kustannukset rivit)
              varillinen-osio)
        varillisen-osion-taulukot (filter #(= :fo:table (first %)) (drop 2 varillinen-osio))]
    (is (some #(= varillinen-osio %) (drop 2 osio)))
    (is (= "solid 0.5mm #1C891C" (get-in varillinen-osio [1 :border-top])))
    (is (= 2 (count varillisen-osion-taulukot)))
    (is (= [:fo:table :fo:table] (map first varillisen-osion-taulukot)))))


