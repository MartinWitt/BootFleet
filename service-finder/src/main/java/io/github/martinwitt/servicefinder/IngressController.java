package io.github.martinwitt.servicefinder;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class IngressController {

    private final IngressService ingressService;

    public IngressController(IngressService ingressService) {
        this.ingressService = ingressService;
    }

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/services")
    public String services(Model model) {
        model.addAttribute("services", ingressService.findAll());
        return "services :: services";
    }
}
