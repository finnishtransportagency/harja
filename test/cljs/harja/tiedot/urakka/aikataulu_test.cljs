(ns harja.tiedot.urakka.aikataulu-test
  (:require [harja.tiedot.urakka.aikataulu :as aikataulu]
            [clojure.test :as t :refer-macros [deftest is testing]]
            [harja.pvm :as pvm]))

(def kohde-valmis :aikataulu-kohde-valmis)
(def kohde-aloitettu :aikataulu-kohde-alku)
(def tiemerkinta-aloitettu :aikataulu-tiemerkinta-alku)
(def tiemerkinta-lopetettu :aikataulu-tiemerkinta-loppu)

(def +paallystys-ja-tiemerkinta-kesken+
  {:aikataulu-kohde-alku (pvm/->pvm "1.2.2017")
   :aikataulu-kohde-valmis nil
   :aikataulu-tiemerkinta-alku (pvm/->pvm "1.3.2017")
   :aikataulu-tiemerkinta-loppu nil})

(def +paallystys-kesken-vaikka-kohde-valmis-koska-valmius-tulevaisuudessa+
  {:aikataulu-kohde-alku (pvm/->pvm "1.2.2017")
   :aikataulu-kohde-valmis (pvm/->pvm "1.2.2037")
   :aikataulu-tiemerkinta-alku (pvm/->pvm "1.3.2017")
   :aikataulu-tiemerkinta-loppu (pvm/->pvm "2.3.2017")})

(def +tiemerkinta-kesken-vaikka-tiemerkinta-valmis-koska-valmius-tulevaisuudessa+
  {:aikataulu-kohde-alku (pvm/->pvm "1.2.2017")
   :aikataulu-kohde-valmis (pvm/->pvm "1.4.2037")
   :aikataulu-tiemerkinta-alku (pvm/->pvm "1.3.2017")
   :aikataulu-tiemerkinta-loppu (pvm/->pvm "2.3.2037")})

(def +paallystys-ja-tiemerkinta-valmis+
  {:aikataulu-kohde-alku (pvm/->pvm "1.2.2017")
   :aikataulu-kohde-valmis (pvm/->pvm "1.3.2017")
   :aikataulu-tiemerkinta-alku (pvm/->pvm "15.2.2017")
   :aikataulu-tiemerkinta-loppu (pvm/->pvm "17.2.2017")})

(def +paallystys-ja-tiemerkinta-aloittamatta1+
  {:aikataulu-kohde-alku nil
   :aikataulu-kohde-valmis nil
   :aikataulu-tiemerkinta-alku nil
   :aikataulu-tiemerkinta-loppu nil})

(def +paallystys-ja-tiemerkinta-aloittamatta2+
  {:aikataulu-kohde-alku (pvm/->pvm "1.1.2040") ; aloitus annettu mutta tulevaisuudessa. Breakkaa 2.1.2040.
   :aikataulu-kohde-valmis (pvm/->pvm "5.1.2040")
   :aikataulu-tiemerkinta-alku (pvm/->pvm "2.1.2040")
   :aikataulu-tiemerkinta-loppu (pvm/->pvm "4.1.2040")})

(deftest aikataulurivin-luokittelu
  (testing "Keskeneräiset kohteet luokitellaan oikein"

    (is (= :kesken (aikataulu/luokittele-valmiuden-mukaan
                     +paallystys-ja-tiemerkinta-kesken+ :paallystys (pvm/nyt))))
    (is (= :kesken (aikataulu/luokittele-valmiuden-mukaan
                     +paallystys-kesken-vaikka-kohde-valmis-koska-valmius-tulevaisuudessa+ :paallystys (pvm/nyt))))

    (is (= :kesken (aikataulu/luokittele-valmiuden-mukaan
                     +paallystys-ja-tiemerkinta-kesken+ :tiemerkinta (pvm/nyt))))
    (is (= :kesken (aikataulu/luokittele-valmiuden-mukaan
                     +tiemerkinta-kesken-vaikka-tiemerkinta-valmis-koska-valmius-tulevaisuudessa+ :tiemerkinta (pvm/nyt)))))

  (testing "Valmiit kohteet luokitellaan oikein"

    (is (= :valmis (aikataulu/luokittele-valmiuden-mukaan
                     +paallystys-ja-tiemerkinta-valmis+ :paallystys (pvm/nyt))))
    (is (= :valmis (aikataulu/luokittele-valmiuden-mukaan
                     +paallystys-ja-tiemerkinta-valmis+ :tiemerkinta (pvm/nyt)))))

  (testing "Aloittamatta olevat kohteet luokitellaan oikein"

    (is (= :aloittamatta (aikataulu/luokittele-valmiuden-mukaan
                     +paallystys-ja-tiemerkinta-aloittamatta1+ :paallystys (pvm/nyt))))
    (is (= :aloittamatta (aikataulu/luokittele-valmiuden-mukaan
                           +paallystys-ja-tiemerkinta-aloittamatta1+ :tiemerkinta (pvm/nyt))))
    (is (= :aloittamatta (aikataulu/luokittele-valmiuden-mukaan
                           +paallystys-ja-tiemerkinta-aloittamatta2+ :paallystys (pvm/nyt))))
    (is (= :aloittamatta (aikataulu/luokittele-valmiuden-mukaan
                           +paallystys-ja-tiemerkinta-aloittamatta2+ :tiemerkinta (pvm/nyt))))))

