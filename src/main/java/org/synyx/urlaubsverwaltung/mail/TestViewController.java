package org.synyx.urlaubsverwaltung.mail;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/max/test")
class TestViewController {

    private final MonthlyEmailScheduler monthlyEmailScheduler;

    @Autowired
    public TestViewController(MonthlyEmailScheduler monthlyEmailScheduler) {
        this.monthlyEmailScheduler = monthlyEmailScheduler;
    }

    @GetMapping("/download")
    public ResponseEntity<ByteArrayResource> downloadCSV() {
        monthlyEmailScheduler.sendMonthlyEmail();
        return ResponseEntity.ok().build();
    }
}
