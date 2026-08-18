(ns harja.views.urakka.valikatselmus.yhteenveto.luvut
  (:require [harja.tiedot.urakka.valikatselmus.valikatselmus-tiedot :as valikatselmus-tiedot]))


(defn arvo-paatoksesta
  "Monet euromääräiset arvot päätöksestä kannattaa hakea vasta, kun päätös on tehty. 
  Ja toisaalta päätöksissä voi olla myös tietoja, jotka voidaan näyttää, vaikka päätöstä ei ole vielä tehty. 
  Eli verrataan tietokanta id:tä siihen, että onko päätös tehty."
  [paatos avain]
  (when (:id paatos)
    (get paatos avain)))


(defn yhteenveto-luvut [{:keys [paatokset urakan-parametrit] :as app}]
  (let [yhteenvedon-tiedot (:yhteenveto app)
        ;; Yhteenvedot kokonaissummat ja tavoitehinnan muodostuminen
        hoitovuoden-alun-indeksikorjattu-tavoitehinta (or (get-in yhteenvedon-tiedot [:budjettitavoite :tavoitehinta-indeksikorjattu]) 0)
        ;; Tavoitehinnan muutokset saadaan oikaisuista -24 ja sitä vanhemmille urakoille
        tavoitehinnan-muutokset (or (get-in yhteenvedon-tiedot [:kustannukset :tavoitehinnanoikaisu-budjetoitu]) 0)
        aktiiviset-pysyvat-muutokset (when (:muutosten_hallinta urakan-parametrit)
                                       (get-in yhteenvedon-tiedot [:budjettitavoite :muutos-summa]))
        menneet-pysyvat-muutokset (when (:muutosten_hallinta urakan-parametrit)
                                    (get-in yhteenvedon-tiedot [:budjettitavoite :menneet-muutos-summa]))
        toteumiin-perustuvat-muutokset-yht (when (:muutosten_hallinta urakan-parametrit)
                                             (:toteumiin-perustuvat-muutokset-yht yhteenvedon-tiedot))
        pysyvat-muutokset-toteuma-muutokset-yht (+ (or aktiiviset-pysyvat-muutokset 0) (or toteumiin-perustuvat-muutokset-yht 0))
        arvonvahennykset-yht (apply + (map #(:maara %) (:arvonvahennykset yhteenvedon-tiedot)))

        ;; Hoitovuoden lopun indeksikorjaus -päätös vaikuttaa myös hoitovuoden lopun tavoitehintaan.
        hv-lopun-indkorjaus-paatos (valikatselmus-tiedot/ota-paatos paatokset :hoitovuoden-lopun-indeksikorjaus)
        hoitokauden_lopun_indeksikorjaus (or (:hoitokauden_lopun_indeksikorjaus hv-lopun-indkorjaus-paatos) 0)

        ;; Hoitovuoden lopun tavoitehinta tulee budjettitavoite -hausta, jossa on mukana vain tietokantaan suoraan tallennettu hoitokauden lopun tavoitehinta.
        ;; Se ei siis ota huomioon muutoksia tai arvonvähennyksiä.
        hoitovuoden-lopun-tavoitehinta (or (get-in yhteenvedon-tiedot [:budjettitavoite :hoitovuoden-lopun-tavoitehinta]) 0)
        ;; Hoitovuoden lopun tavoitehintaan vaikuttavat myös mahdolliset kirjallisesti sovitut muutokset ja toteumiin perustuvat muutokset
        ;; Sekä arvonvähennykset
        hoitovuoden-lopun-tavoitehinta (+ hoitovuoden-lopun-tavoitehinta
                                         ;; Jos päätös on tehty, niin indeksikorjaus on jo luvuissa mukana
                                         (if (:id hv-lopun-indkorjaus-paatos) 0 hoitokauden_lopun_indeksikorjaus)
                                         pysyvat-muutokset-toteuma-muutokset-yht
                                         arvonvahennykset-yht)
        hoitovuoden-lopun-kattohinta (or (get-in yhteenvedon-tiedot [:budjettitavoite :hoitovuoden-lopun-kattohinta]) 0)
        ;; Hoitovuoden lopun tavoitehintaan vaikuttavat myös mahdolliset kirjallisesti sovitut muutokset ja toteumiin perustuvat muutokset
        ;; Sekä arvonvähennykset
        hoitovuoden-lopun-kattohinta (+ hoitovuoden-lopun-kattohinta
                                       (* (if (:id hv-lopun-indkorjaus-paatos) 0 hoitokauden_lopun_indeksikorjaus) (:hoitokauden_lopun_kattohinta_kerroin urakan-parametrit))
                                       (* pysyvat-muutokset-toteuma-muutokset-yht (:hoitokauden_lopun_kattohinta_kerroin urakan-parametrit))
                                       (* arvonvahennykset-yht (:hoitokauden_lopun_kattohinta_kerroin urakan-parametrit)))]
    {:yhteenvedon-tiedot yhteenvedon-tiedot
     :hoitovuoden-alun-indeksikorjattu-tavoitehinta hoitovuoden-alun-indeksikorjattu-tavoitehinta
     :tavoitehinnan-muutokset tavoitehinnan-muutokset
     :aktiiviset-pysyvat-muutokset aktiiviset-pysyvat-muutokset
     :menneet-pysyvat-muutokset menneet-pysyvat-muutokset
     :toteumiin-perustuvat-muutokset-yht toteumiin-perustuvat-muutokset-yht
     :pysyvat-muutokset-toteuma-muutokset-yht pysyvat-muutokset-toteuma-muutokset-yht
     :arvonvahennykset-yht arvonvahennykset-yht
     :hv-lopun-indkorjaus-paatos hv-lopun-indkorjaus-paatos
     :hoitokauden_lopun_indeksikorjaus hoitokauden_lopun_indeksikorjaus
     :hoitovuoden-lopun-tavoitehinta hoitovuoden-lopun-tavoitehinta
     :hoitovuoden-lopun-kattohinta hoitovuoden-lopun-kattohinta}))
