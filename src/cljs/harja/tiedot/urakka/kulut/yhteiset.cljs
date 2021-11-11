(ns harja.tiedot.urakka.kulut.yhteiset
  (:require
    [harja.domain.kulut.valikatselmus :as valikatselmus]
    [harja.domain.muokkaustiedot :as muokkaustiedot]
    [harja.tiedot.urakka :as urakka-tiedot]
    [harja.tiedot.urakka.kulut.mhu-kustannusten-seuranta :as kustannusten-seuranta-tiedot]
    [harja.pvm :as pvm]
    [harja.tiedot.urakka.urakka :as tila]))

(def manuaalisen-kattohinnan-syoton-vuodet
  [2019 2020])

(defn oikaisujen-summa [oikaisut hoitokauden-alkuvuosi]
  (or (apply + (map ::valikatselmus/summa (filter
                                            #(and (not (or (:poistettu %) (::muokkaustiedot/poistettu? %))))
                                            (-> oikaisut (get hoitokauden-alkuvuosi) vals)))) 0))

(defn valikatselmus-tekematta? [app]
  (let [valittu-hoitokauden-alkuvuosi (:hoitokauden-alkuvuosi app)
        valittu-hoitovuosi-nro (urakka-tiedot/hoitokauden-jarjestysnumero valittu-hoitokauden-alkuvuosi (-> @tila/yleiset :urakka :loppupvm))
        tavoitehinta (or (kustannusten-seuranta-tiedot/hoitokauden-tavoitehinta valittu-hoitovuosi-nro app) 0)
        kattohinta (or (kustannusten-seuranta-tiedot/hoitokauden-oikaistu-kattohinta valittu-hoitovuosi-nro app) 0)
        toteuma (or (get-in app [:kustannukset-yhteensa :yht-toteutunut-summa]) 0)
        oikaisujen-summa (oikaisujen-summa (:tavoitehinnan-oikaisut app) valittu-hoitokauden-alkuvuosi)
        oikaistu-tavoitehinta (+ tavoitehinta oikaisujen-summa)
        oikaistu-kattohinta (+ kattohinta oikaisujen-summa)
        urakan-paatokset (:urakan-paatokset app)
        filtteroi-paatos-fn (fn [paatoksen-tyyppi]
                              (first (filter #(and (= (::valikatselmus/hoitokauden-alkuvuosi %) valittu-hoitokauden-alkuvuosi)
                                                (= (::valikatselmus/tyyppi %) (name paatoksen-tyyppi))) urakan-paatokset)))
        tavoitehinta-alitettu? (> oikaistu-tavoitehinta toteuma)
        tavoitehinta-ylitetty? (> toteuma oikaistu-tavoitehinta)
        kattohinta-ylitetty? (> toteuma oikaistu-kattohinta)
        tavoitehinnan-alitus-paatos (filtteroi-paatos-fn :tavoitehinnan-alitus)
        tavoitehinnan-ylitys-paatos (filtteroi-paatos-fn :tavoitehinnan-ylitys)
        kattohinnan-ylitys-paatos (filtteroi-paatos-fn :kattohinnan-ylitys)]
    (and
      (<= valittu-hoitokauden-alkuvuosi (pvm/vuosi (pvm/nyt)))
      (or
        (and tavoitehinta-alitettu? (nil? tavoitehinnan-alitus-paatos))
        (and tavoitehinta-ylitetty? (nil? tavoitehinnan-ylitys-paatos))
        (and kattohinta-ylitetty? (nil? kattohinnan-ylitys-paatos))))))
