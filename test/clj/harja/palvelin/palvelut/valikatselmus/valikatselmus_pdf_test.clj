(ns harja.palvelin.palvelut.valikatselmus.valikatselmus-pdf-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.palvelut.valikatselmus.valikatselmus-pdf :as valikatselmus-pdf]))

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
  (let [rivit (valikatselmus-pdf/yhteenveto-rivit perusdata)
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
  (let [rivit (valikatselmus-pdf/yhteenveto-rivit perusdata)
        osio ((ns-resolve 'harja.palvelin.palvelut.valikatselmus.valikatselmus-pdf 'osio)
              "Tavoitehintaan kuuluvat toteutuneet kustannukset"
              (:kustannukset rivit)
              ((ns-resolve 'harja.palvelin.palvelut.valikatselmus.valikatselmus-pdf 'varillinen-osio)
               :vihrea
               (:tavoitehinnan-alitus rivit)))]
    (is (= :fo:block (first (nth osio 4))))))


